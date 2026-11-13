package com.businessledger;

import java.sql.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Dedicated short-lived JDBC connections keep timed snapshots off the UI connection. */
final class PeriodicBackups implements AutoCloseable {
    static final int MINUTES=10;
    @FunctionalInterface interface Connections {Connection open()throws SQLException;}
    private final Connections connections;private final String ledger;private final BooleanSupplier paused;private final Consumer<Exception> failure;
    private volatile boolean closed;private String lastError;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(task->{Thread thread=new Thread(task,"ledger-timed-backups");thread.setDaemon(true);return thread;});
    PeriodicBackups(Connections connections,String ledger,BooleanSupplier paused,Consumer<Exception> failure){this.connections=connections;this.ledger=ledger;this.paused=paused;this.failure=failure;}
    void start(){worker.scheduleWithFixedDelay(this::tick,MINUTES,MINUTES,TimeUnit.MINUTES);}
    void tick(){
        if(closed||paused.getAsBoolean())return;
        try(Connection db=connections.open()){
            if(new LedgerBackup(db).options().timed())AutomaticBackups.take(db,AutomaticBackups.Kind.TIMED,ledger);
            lastError=null;
        }catch(Exception ex){String message=ex.getClass().getName()+":"+ex.getMessage();if(!closed&&!paused.getAsBoolean()&&!Thread.currentThread().isInterrupted()&&!java.util.Objects.equals(lastError,message)){lastError=message;failure.accept(ex);}}
    }
    @Override public void close(){closed=true;worker.shutdownNow();}
}
