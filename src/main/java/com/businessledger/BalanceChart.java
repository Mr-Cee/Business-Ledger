package com.businessledger;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

final class BalanceChart extends JPanel implements Scrollable {
    enum Frequency { DAILY, MONTHLY, YEARLY }
    record Series(long id,String name,List<BigDecimal> balances) {}
    record Data(List<LocalDate> dates,List<Series> series) {}
    private static final NumberFormat MONEY=NumberFormat.getCurrencyInstance(Locale.US);
    private Data data=new Data(List.of(),List.of());
    BalanceChart(){setBackground(AppTheme.surface());setToolTipText("Account balance");setPreferredSize(new Dimension(800,350));}
    static Data load(Connection db,LocalDate start,LocalDate end,Frequency frequency)throws SQLException {
        if(start.isAfter(end))throw new IllegalArgumentException("From date must be on or before the through date.");
        List<LocalDate> dates=new ArrayList<>();LocalDate date=start;
        while(!date.isAfter(end)) {
            LocalDate close=switch(frequency){case DAILY->date;case MONTHLY->YearMonth.from(date).atEndOfMonth();case YEARLY->LocalDate.of(date.getYear(),12,31);};
            if(close.isAfter(end))close=end;dates.add(close);if(close.equals(end))break;date=close.plusDays(1);
        }
        Map<Long,NavigableMap<LocalDate,BigDecimal>> flows=new HashMap<>();
        try(PreparedStatement s=db.prepareStatement("""
                WITH events AS (
                    SELECT account_id,entry_date AS day,CASE WHEN kind='Income' THEN amount ELSE -amount END AS amount FROM transactions WHERE account_id IS NOT NULL
                    UNION ALL SELECT from_account_id,transfer_date,-amount FROM transfers
                    UNION ALL SELECT to_account_id,transfer_date,amount FROM transfers
                ) SELECT account_id,day,SUM(amount) FROM events WHERE day<=? GROUP BY account_id,day ORDER BY account_id,day
                """)) {
            s.setObject(1,end);try(ResultSet r=s.executeQuery()){while(r.next())flows.computeIfAbsent(r.getLong(1),k->new TreeMap<>()).put(r.getDate(2).toLocalDate(),r.getBigDecimal(3));}
        }
        List<Series> series=new ArrayList<>();
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT id,name,opening_balance,active FROM accounts ORDER BY name")) {
            while(r.next()) {
                long id=r.getLong(1);String name=r.getString(2)+(r.getBoolean(4)?"":" (archived)");BigDecimal balance=r.getBigDecimal(3);
                Iterator<Map.Entry<LocalDate,BigDecimal>> movements=flows.getOrDefault(id,new TreeMap<>()).entrySet().iterator();Map.Entry<LocalDate,BigDecimal> next=movements.hasNext()?movements.next():null;
                List<BigDecimal> values=new ArrayList<>();
                for(LocalDate point:dates) {
                    while(next!=null && !next.getKey().isAfter(point)){balance=balance.add(next.getValue());next=movements.hasNext()?movements.next():null;}
                    values.add(balance);
                }
                series.add(new Series(id,name,List.copyOf(values)));
            }
        }
        return new Data(List.copyOf(dates),List.copyOf(series));
    }
    static Color color(long id){return Color.getHSBColor((float)((id*.61803398875)%1),.7f,.72f);}
    void setData(Data value){data=value;repaint();}
    public Dimension getPreferredScrollableViewportSize(){return new Dimension(600,350);}
    public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction){return 30;}
    public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction){return Math.max(30,visible.height-30);}
    public boolean getScrollableTracksViewportWidth(){return true;}
    public boolean getScrollableTracksViewportHeight(){return true;}
    private double[] range(){double min=0,max=0;for(Series s:data.series())for(BigDecimal value:s.balances()){min=Math.min(min,value.doubleValue());max=Math.max(max,value.doubleValue());}if(min==max)max=min+1;return new double[]{min,max};}
    private int left(FontMetrics fm,double[] range){int longest=0;for(int i=0;i<=4;i++)longest=Math.max(longest,fm.stringWidth(MONEY.format(range[0]+(range[1]-range[0])*i/4)));return Math.min(Math.max(75,longest+16),Math.max(75,getWidth()/3));}
    private int x(LocalDate date,int left,int width){long days=ChronoUnit.DAYS.between(data.dates().get(0),data.dates().get(data.dates().size()-1));return days==0?left+width/2:left+(int)(ChronoUnit.DAYS.between(data.dates().get(0),date)*width/(double)days);}
    @Override protected void paintComponent(Graphics graphics){
        setBackground(AppTheme.surface());super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setFont(getFont());
            if(data.series().isEmpty() || data.dates().isEmpty()){g.setColor(AppTheme.text());g.drawString("Choose dates and refresh to view account balances.",16,36);return;}
            FontMetrics fm=g.getFontMetrics();double[] range=range();int left=left(fm,range),top=24,bottom=Math.max(top+80,getHeight()-fm.getHeight()-26),width=Math.max(1,getWidth()-left-30),height=bottom-top;
            for(int tick=0;tick<=4;tick++) {
                int y=bottom-tick*height/4;double value=range[0]+(range[1]-range[0])*tick/4;
                g.setColor(AppTheme.grid());g.drawLine(left,y,left+width,y);g.setColor(AppTheme.text());String label=MONEY.format(value);g.drawString(label,Math.max(4,left-fm.stringWidth(label)-10),y+fm.getAscent()/2);
            }
            int zero=bottom-(int)((0-range[0])/(range[1]-range[0])*height);g.setColor(new Color(160,165,175));g.drawLine(left,zero,left+width,zero);
            int ticks=Math.min(data.dates().size(),Math.max(2,width/150));Set<Integer> indices=new TreeSet<>();
            for(int i=0;i<ticks;i++)indices.add(ticks==1?0:(int)((long)i*(data.dates().size()-1)/(ticks-1)));
            for(int index:indices){LocalDate day=data.dates().get(index);String label=day.toString();int pos=x(day,left,width);g.setColor(AppTheme.text());g.drawString(label,Math.min(getWidth()-fm.stringWidth(label)-4,Math.max(4,pos-fm.stringWidth(label)/2)),bottom+fm.getHeight()+8);}
            Shape clip=g.getClip();g.clipRect(left-4,top-4,width+8,height+8);
            for(Series s:data.series()) {
                Path2D path=new Path2D.Double();g.setColor(color(s.id()));g.setStroke(new BasicStroke(2.2f));
                for(int i=0;i<data.dates().size();i++) {
                    int px=x(data.dates().get(i),left,width),py=bottom-(int)((s.balances().get(i).doubleValue()-range[0])/(range[1]-range[0])*height);
                    if(i==0)path.moveTo(px,py);else path.lineTo(px,py);
                    if(data.dates().size()<=36)g.fillOval(px-3,py-3,6,6);
                }g.draw(path);
            }g.setClip(clip);
        }finally{g.dispose();}
    }
    @Override public String getToolTipText(java.awt.event.MouseEvent event){
        if(data.dates().isEmpty() || data.series().isEmpty())return null;
        double[] range=range();int left=left(getFontMetrics(getFont()),range),width=Math.max(1,getWidth()-left-30),bottom=Math.max(104,getHeight()-getFontMetrics(getFont()).getHeight()-26),height=bottom-24;
        int point=0,best=Integer.MAX_VALUE;for(int i=0;i<data.dates().size();i++){int distance=Math.abs(event.getX()-x(data.dates().get(i),left,width));if(distance<best){best=distance;point=i;}}
        Series closest=null;best=Integer.MAX_VALUE;
        for(Series s:data.series()){int y=bottom-(int)((s.balances().get(point).doubleValue()-range[0])/(range[1]-range[0])*height);int distance=Math.abs(y-event.getY());if(distance<best){best=distance;closest=s;}}
        return data.dates().get(point)+" — "+closest.name()+": "+MONEY.format(closest.balances().get(point));
    }
}
