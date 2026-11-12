package com.businessledger;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.nio.file.*;
import java.io.IOException;

/** Application-wide palettes, stored locally and applied without restarting. */
final class AppTheme {
    enum Style {
        OCEAN("Ocean", 0xEDF3F8, 0xFFFFFF, 0x203449, 0x246FA6),
        FOREST("Forest", 0xEFF4EF, 0xFFFFFF, 0x243B30, 0x347553),
        PLUM("Plum", 0xF4EFF7, 0xFFFFFF, 0x3D2B48, 0x805398),
        MIDNIGHT("Midnight", 0x202936, 0x2B3646, 0xEDF2F7, 0x72B5E8);
        final String label; final Color background, surface, text, accent;
        Style(String label,int background,int surface,int text,int accent){
            this.label=label;this.background=new ColorUIResource(background);this.surface=new ColorUIResource(surface);
            this.text=new ColorUIResource(text);this.accent=new ColorUIResource(accent);
        }
        @Override public String toString(){return label;}
    }
    private static Style current=Style.OCEAN;
    private static Path settings(){return Path.of(System.getProperty("ledger.home","."),".local","appearance.txt");}
    static Style current(){return current;}
    static boolean dark(){return current==Style.MIDNIGHT;}
    static Color surface(){return new Color(current.surface.getRGB());}
    static Color text(){return current.text;}
    static Color muted(){return dark()?new Color(177,193,211):new Color(105,111,120);}
    static Color grid(){return dark()?new Color(65,80,99):new Color(225,230,235);}
    static Color band(){return dark()?new Color(34,45,60):new Color(248,250,252);}
    static Color money(boolean incoming){return dark()?(incoming?new Color(37,74,60):new Color(81,45,51)):(incoming?new Color(231,246,233):new Color(255,233,233));}
    static void initialize() throws Exception {
        try{current=Style.valueOf(Files.readString(settings()).trim());}catch(IOException|IllegalArgumentException ignored){}
        apply(current);
    }
    static void apply(Style style) throws Exception {
        current=style;
        // Recreate Nimbus so its derived colors and painter caches use the new palette.
        UIManager.put("control",style.background);UIManager.put("nimbusBase",style.accent);
        UIManager.put("nimbusBlueGrey",style.background);UIManager.put("nimbusLightBackground",style.surface);
        UIManager.put("text",style.text);UIManager.put("nimbusSelectedText",new ColorUIResource(Color.WHITE));
        UIManager.put("nimbusSelectionBackground",new ColorUIResource(dark()?0x315F88:style.accent.getRGB()));
        UIManager.put("nimbusFocus",style.accent);UIManager.put("nimbusDisabledText",new ColorUIResource(muted()));
        UIManager.setLookAndFeel(new javax.swing.plaf.nimbus.NimbusLookAndFeel());
        UIManager.put("defaultFont",new FontUIResource("Segoe UI",Font.PLAIN,14));
        for(Object key:java.util.Collections.list(UIManager.getDefaults().keys())){
            if(key.toString().endsWith(".font") && UIManager.get(key) instanceof Font font)
                UIManager.put(key,new FontUIResource("Segoe UI",font.getStyle(),14));
        }
        for(String key:new String[]{"Panel.background","Viewport.background","TabbedPane.background"})UIManager.put(key,style.background);
        for(String key:new String[]{"Table.background","List.background","TextField.background","TextArea.background","PasswordField.background","FormattedTextField.background"})UIManager.put(key,style.surface);
        for(String key:new String[]{"Label.foreground","Table.foreground","List.foreground","TextField.foreground","TextArea.foreground","PasswordField.foreground","CheckBox.foreground","Button.foreground","ComboBox.foreground","TabbedPane.foreground"})UIManager.put(key,style.text);
        UIManager.put("Table.rowHeight",30);UIManager.put("Table.showGrid",false);UIManager.put("Table.intercellSpacing",new Dimension(0,1));
        UIManager.put("Table.alternateRowColor",new ColorUIResource(band()));
        UIManager.put("Table.gridColor",new ColorUIResource(grid()));
        UIManager.put("ScrollBar.width",14);
        UIManager.put("Button.margin",new Insets(7,14,7,14));
        UIManager.put("TabbedPane.contentMargins",new Insets(12,12,12,12));
        UIManager.put("TabbedPaneUI","javax.swing.plaf.basic.BasicTabbedPaneUI");
        UIManager.put("ComboBoxUI","com.businessledger.ThemeComboBoxUI");
        UIManager.put("ComboBox.background",style.surface);
        UIManager.put("ComboBox.selectionBackground",new ColorUIResource(dark()?0x315F88:style.accent.getRGB()));
        UIManager.put("ComboBox.selectionForeground",new ColorUIResource(Color.WHITE));
        UIManager.put("TabbedPane.selected",style.surface);
        UIManager.put("TabbedPane.focus",style.accent);
        UIManager.put("TabbedPane.contentAreaColor",style.background);
        UIManager.put("TabbedPane.tabInsets",new Insets(10,16,10,16));
        for(String state:new String[]{"Enabled","Disabled","Focused","MouseOver","Focused+MouseOver","Pressed","Focused+Pressed"}){
            Color fill=state.contains("Pressed")?band():state.contains("MouseOver")?band():surface();
            UIManager.put("Button["+state+"].backgroundPainter",painter(fill));
            UIManager.put("ComboBox["+state+"].backgroundPainter",painter(fill));
            UIManager.put("ComboBox["+state+"].textForeground",style.text);
        }
        for(String state:new String[]{"Enabled","Enabled+MouseOver","Enabled+Pressed","Selected","Selected+MouseOver","Selected+Pressed","Focused+Selected","Focused+Selected+MouseOver","Focused+Selected+Pressed","Disabled","Disabled+Selected"}){
            UIManager.put("TabbedPane:TabbedPaneTab["+state+"].backgroundPainter",painter(state.contains("Selected")?surface():style.background));
            UIManager.put("TabbedPane:TabbedPaneTab["+state+"].textForeground",style.text);
        }
        UIManager.put("ComboBox.rendererUseListColors",true);
        UIManager.put("TableHeader.foreground",style.text);
        UIManager.put("TableHeader.background",style.background);
        for(String state:new String[]{"Enabled","Enabled+Focused","Enabled+MouseOver","Enabled+Pressed","Enabled+Sorted","Enabled+Focused+Sorted","Disabled","Disabled+Sorted"}){
            UIManager.put("TableHeader:\"TableHeader.renderer\"["+state+"].backgroundPainter",painter(style.background));
            UIManager.put("TableHeader:\"TableHeader.renderer\"["+state+"].textForeground",style.text);
        }
        for(Window window:Window.getWindows()){
            SwingUtilities.updateComponentTreeUI(window);refresh(window);window.invalidate();window.validate();window.repaint();
        }
    }
    private static Painter<JComponent> painter(Color fill){
        Color outline=grid();return (g,c,w,h)->{
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);g.fillRoundRect(1,1,w-2,h-2,8,8);
            g.setColor(outline);g.drawRoundRect(1,1,w-3,h-3,8,8);
        };
    }
    private static void refresh(Component component){
        if(component instanceof MonthlyChart || component instanceof BalanceChart || component instanceof TransferChart || component instanceof PayeePieChart || component instanceof ChartsPanel.BarChart)component.setBackground(surface());
        if(component instanceof JTextArea area && !area.isEditable())area.setBackground(current.background);
        if(component instanceof Container container)for(Component child:container.getComponents())refresh(child);
    }
    static void save(Style style)throws Exception{
        apply(style);Files.createDirectories(settings().getParent());Files.writeString(settings(),style.name());
    }
}
