"""Verify packaged native imports without accepting system-installed C++ runtimes."""
import json
import os
import struct
import sys
from pathlib import Path


class PE:
    def __init__(self, path):
        self.data = path.read_bytes()
        if self.data[:2] != b"MZ":
            raise ValueError(f"Not a native executable: {path}")
        header = struct.unpack_from("<I", self.data, 0x3C)[0]
        if self.data[header:header + 4] != b"PE\0\0":
            raise ValueError(f"Invalid native file: {path}")
        self.machine, count = struct.unpack_from("<HH", self.data, header + 4)
        optional_size = struct.unpack_from("<H", self.data, header + 20)[0]
        optional = header + 24
        self.wide = struct.unpack_from("<H", self.data, optional)[0] == 0x20B
        self.directories = optional + (112 if self.wide else 96)
        self.sections = []
        for index in range(count):
            offset = optional + optional_size + index * 40
            virtual_size, address, size, raw = struct.unpack_from("<IIII", self.data, offset + 8)
            self.sections.append((address, max(virtual_size, size), raw))

    def rva(self, address):
        for start, size, raw in self.sections:
            if start <= address < start + size:
                return raw + address - start
        if address < 4096:
            return address
        raise ValueError(f"Unmapped RVA: {address}")

    def string(self, address):
        offset = self.rva(address)
        return self.data[offset:self.data.index(b"\0", offset)].decode("ascii")

    def imports(self):
        address = struct.unpack_from("<I", self.data, self.directories + 8)[0]
        if not address:
            return
        offset = self.rva(address)
        while True:
            thunk, _, _, name, first = struct.unpack_from("<IIIII", self.data, offset)
            if not name:
                break
            functions = []
            entry = self.rva(thunk or first)
            width, fmt, ordinal = (8, "<Q", 1 << 63) if self.wide else (4, "<I", 1 << 31)
            while True:
                value = struct.unpack_from(fmt, self.data, entry)[0]
                if not value:
                    break
                if not value & ordinal:
                    functions.append(self.string(value + 2))
                entry += width
            yield self.string(name).lower(), functions
            offset += 20

    def exports(self):
        address = struct.unpack_from("<I", self.data, self.directories)[0]
        if not address:
            return set()
        offset = self.rva(address)
        count = struct.unpack_from("<I", self.data, offset + 24)[0]
        names = self.rva(struct.unpack_from("<I", self.data, offset + 32)[0])
        return {self.string(struct.unpack_from("<I", self.data, names + index * 4)[0]) for index in range(count)}


def main():
    image = Path(sys.argv[1]).resolve()
    files = {}
    for file in image.rglob("*"):
        if file.suffix.lower() in {".dll", ".exe"} and "licenses" not in file.parts:
            files.setdefault(file.name.lower(), []).append(file)
    required = [*image.glob("*.exe"), *image.glob("runtime/bin/*.exe"), *image.glob("runtime/bin/*.dll"),
                *image.glob("runtime/bin/server/*.dll"), *image.glob("postgres/bin/*.exe"), image / "postgres/lib/plpgsql.dll"]
    pending, seen, errors, os_dependencies = list(required), set(), [], set()
    export_cache = {}
    while pending:
        file = pending.pop()
        if file in seen:
            continue
        seen.add(file)
        pe = PE(file)
        if pe.machine not in {0x8664, 0x14C}:
            errors.append(f"Unsupported machine in {file.relative_to(image)}")
        for library, functions in pe.imports():
            if library.startswith(("api-ms-", "ext-ms-")):
                continue
            candidates = files.get(library, [])
            native_crt = library.startswith(("msvcp", "vcruntime", "concrt")) or (library.startswith("msvcr") and library != "msvcrt.dll")
            if not candidates:
                if native_crt or not (Path(os.environ["WINDIR"]) / "System32" / library).exists():
                    errors.append(f"Missing {library} required by {file.relative_to(image)}")
                else:
                    os_dependencies.add(library)
                continue
            dependency = next((path for path in candidates if path.parent == file.parent), candidates[0])
            pending.append(dependency)
            if native_crt:
                exports = export_cache.setdefault(dependency, PE(dependency).exports())
                for function in functions:
                    if function not in exports:
                        errors.append(f"{library} lacks {function} required by {file.relative_to(image)}")
    if errors:
        raise RuntimeError("\n".join(sorted(set(errors))))
    report = {"native_files_checked": len(seen), "windows_components": sorted(os_dependencies), "system_cpp_runtime_fallback": False}
    (image / "native-dependency-audit.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(f"Native dependency audit passed for {len(seen)} files; C++ imports resolve to packaged DLLs.")


if __name__ == "__main__":
    main()
