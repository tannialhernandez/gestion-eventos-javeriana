package com.javeriana.eventos.event.infrastructure.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "retention")
public class RetentionConfig {
    private boolean enabled = true;
    private String cron = "0 0 3 * * *";
    private int outboxEnviadoDays = 30;
    private int outboxFallidoDays = 90;
    private int mensajeProcesadoDays = 30;
    private int batchSize = 1000;
    private int maxBatchesPerRun = 100;

    public boolean isEnabled()            { return enabled; }
    public void setEnabled(boolean e)     { this.enabled = e; }
    public String getCron()               { return cron; }
    public void setCron(String c)         { this.cron = c; }
    public int getOutboxEnviadoDays()     { return outboxEnviadoDays; }
    public void setOutboxEnviadoDays(int d) { this.outboxEnviadoDays = d; }
    public int getOutboxFallidoDays()     { return outboxFallidoDays; }
    public void setOutboxFallidoDays(int d) { this.outboxFallidoDays = d; }
    public int getMensajeProcesadoDays() { return mensajeProcesadoDays; }
    public void setMensajeProcesadoDays(int d) { this.mensajeProcesadoDays = d; }
    public int getBatchSize()             { return batchSize; }
    public void setBatchSize(int s)       { this.batchSize = s; }
    public int getMaxBatchesPerRun()      { return maxBatchesPerRun; }
    public void setMaxBatchesPerRun(int m){ this.maxBatchesPerRun = m; }
}
