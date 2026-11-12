package com.businessledger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

/** Paired account movements and a ranked view of directed transfer routes. */
final class TransferChart extends JPanel implements Scrollable {
    record Account(long id,String name,BigDecimal incoming,BigDecimal outgoing,long count){BigDecimal net(){return incoming.subtract(outgoing);}}
    record Route(String from,String to,BigDecimal amount,long count){}
    record Data(List<Account> accounts,List<Route> routes,BigDecimal total,long count){}
    static Data empty(){return new Data(List.of(),List.of(),BigDecimal.ZERO,0);}
    private static final NumberFormat MONEY=NumberFormat.getCurrencyInstance(Locale.US);
    private static final Color IN=new Color(55,125,190),OUT=new Color(210,137,49),FLOW=new Color(42,143,151);
    private Data data=empty();private boolean routes;private int hovered=-1;
    TransferChart(){
        setBackground(AppTheme.surface());setToolTipText("Hover over a transfer row for exact values.");
        addMouseMotionListener(new MouseMotionAdapter(){@Override public void mouseMoved(MouseEvent e){int row=rowAt(e.getY());if(hovered!=row){hovered=row;repaint();}}});
        addMouseListener(new MouseAdapter(){@Override public void mouseExited(MouseEvent e){hovered=-1;repaint();}});
    }
    static Data load(Connection db,LocalDate start,LocalDate end)throws SQLException{
        if(start.isAfter(end))throw new IllegalArgumentException("From date must be on or before the through date.");
        Map<Long,Account> accounts=new LinkedHashMap<>();List<Route> routes=new ArrayList<>();BigDecimal total=BigDecimal.ZERO;long count=0;
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT id,name,active FROM accounts ORDER BY name")){
            while(r.next())accounts.put(r.getLong(1),new Account(r.getLong(1),r.getString(2)+(r.getBoolean(3)?"":" (archived)"),BigDecimal.ZERO,BigDecimal.ZERO,0));
        }
        try(PreparedStatement s=db.prepareStatement("SELECT from_account_id,to_account_id,SUM(amount),COUNT(*) FROM transfers WHERE transfer_date BETWEEN ? AND ? GROUP BY from_account_id,to_account_id ORDER BY SUM(amount) DESC,from_account_id,to_account_id")){
            s.setObject(1,start);s.setObject(2,end);try(ResultSet r=s.executeQuery()){while(r.next()){
                long from=r.getLong(1),to=r.getLong(2);BigDecimal amount=r.getBigDecimal(3);long times=r.getLong(4);Account a=accounts.get(from),b=accounts.get(to);
                if(a==null||b==null)throw new SQLException("A transfer references a missing account.");
                routes.add(new Route(a.name(),b.name(),amount,times));total=total.add(amount);count+=times;
                accounts.put(from,new Account(a.id(),a.name(),a.incoming(),a.outgoing().add(amount),a.count()+times));
                accounts.put(to,new Account(b.id(),b.name(),b.incoming().add(amount),b.outgoing(),b.count()+times));
            }}
        }
        return new Data(List.copyOf(accounts.values()),List.copyOf(routes),total,count);
    }
    void setData(Data value,boolean byRoute){data=value;routes=byRoute;hovered=-1;revalidate();repaint();}
    private int rowHeight(){return Math.max(routes?78:96,getFontMetrics(getFont()).getHeight()*(routes?3:4)+20);}
    private int rowAt(int y){int row=(y-76)/rowHeight();return y>=76&&row>=0&&row<(routes?data.routes().size():data.accounts().size())?row:-1;}
    @Override public Dimension getPreferredSize(){return new Dimension(850,Math.max(240,96+(routes?data.routes().size():data.accounts().size())*rowHeight()));}
    @Override public String getToolTipText(MouseEvent e){
        if(data.count()==0)return null;int row=rowAt(e.getY());if(row<0)return null;
        if(routes){Route route=data.routes().get(row);return "From: "+route.from()+" | To: "+route.to()+" | Transferred: "+MONEY.format(route.amount())+" | Transfers: "+route.count();}
        Account account=data.accounts().get(row);return "Account: "+account.name()+" | In: "+MONEY.format(account.incoming())+" | Out: "+MONEY.format(account.outgoing())+" | Net movement: "+MONEY.format(account.net())+" | Transfers: "+account.count();
    }
    @Override protected void paintComponent(Graphics graphics){
        setBackground(AppTheme.surface());super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();
        try{
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setFont(getFont());
            if(data.count()==0){g.setColor(AppTheme.text());g.drawString("No transfers in this date range.",24,42);return;}
            FontMetrics fm=g.getFontMetrics();int left=Math.min(220,Math.max(130,getWidth()/4)),valueWidth=80;
            double maximum=0;
            if(routes){for(Route route:data.routes()){maximum=Math.max(maximum,route.amount().doubleValue());valueWidth=Math.max(valueWidth,fm.stringWidth(MONEY.format(route.amount()))+18);}}
            else for(Account account:data.accounts()){
                maximum=Math.max(maximum,Math.max(account.incoming().doubleValue(),account.outgoing().doubleValue()));
                valueWidth=Math.max(valueWidth,Math.max(fm.stringWidth(MONEY.format(account.incoming())),fm.stringWidth(MONEY.format(account.outgoing())))+18);
            }
            int plotLeft=left+26,plotWidth=Math.max(1,getWidth()-plotLeft-valueWidth-24);maximum=Math.max(1,maximum);
            if(routes){g.setColor(FLOW);g.fillRoundRect(18,17,13,13,3,3);g.setColor(AppTheme.text());g.drawString("Transfers between accounts",39,29);}
            else{
                g.setColor(IN);g.fillRoundRect(18,17,13,13,3,3);g.setColor(AppTheme.text());g.drawString("Money transferred in",39,29);
                int next=60+fm.stringWidth("Money transferred in");g.setColor(OUT);g.fillRoundRect(next,17,13,13,3,3);g.setColor(AppTheme.text());g.drawString("Money transferred out",next+21,29);
            }
            g.setColor(AppTheme.muted());g.drawString("$0",plotLeft,59);String max=MONEY.format(maximum);g.drawString(max,plotLeft+plotWidth-fm.stringWidth(max),59);
            int rows=routes?data.routes().size():data.accounts().size(),height=rowHeight();
            for(int row=0;row<rows;row++){
                int y=76+row*height;
                if(row%2==1||row==hovered){g.setColor(AppTheme.band());g.fillRect(0,y,getWidth(),height);}
                g.setColor(AppTheme.grid());g.drawLine(plotLeft,y+8,plotLeft,y+height-10);
                if(routes){
                    Route route=data.routes().get(row);g.setFont(getFont().deriveFont(Font.BOLD));g.setColor(AppTheme.text());g.drawString(shorten(g,"From: "+route.from(),left-26),16,y+25);
                    g.setFont(getFont());g.drawString(shorten(g,"To: "+route.to(),left-26),16,y+fm.getHeight()+25);g.setColor(AppTheme.muted());g.drawString("Transfers: "+route.count(),16,y+fm.getHeight()*2+25);
                    bar(g,route.amount(),FLOW,plotLeft,y+25,plotWidth,maximum,valueWidth);
                }else{
                    Account account=data.accounts().get(row);g.setFont(getFont().deriveFont(Font.BOLD));g.setColor(AppTheme.text());g.drawString(shorten(g,account.name(),left-26),16,y+25);
                    g.setFont(getFont());g.setColor(AppTheme.muted());g.drawString(shorten(g,"Net: "+MONEY.format(account.net()),left-26),16,y+fm.getHeight()+25);g.drawString("Transfers: "+account.count(),16,y+fm.getHeight()*2+25);
                    g.setColor(AppTheme.text());g.drawString("In",left,y+29);g.drawString("Out",left,y+61);
                    bar(g,account.incoming(),IN,plotLeft,y+12,plotWidth,maximum,valueWidth);bar(g,account.outgoing(),OUT,plotLeft,y+44,plotWidth,maximum,valueWidth);
                }
            }
        }finally{g.dispose();}
    }
    private void bar(Graphics2D g,BigDecimal amount,Color color,int x,int y,int width,double max,int reserved){
        g.setColor(color);g.fillRoundRect(x,y,amount.signum()==0?0:Math.max(1,(int)(amount.doubleValue()/max*width)),22,5,5);
        g.setColor(AppTheme.text());g.drawString(MONEY.format(amount),getWidth()-reserved+4,y+17);
    }
    private static String shorten(Graphics2D g,String label,int width){if(g.getFontMetrics().stringWidth(label)<=width)return label;while(!label.isEmpty()&&g.getFontMetrics().stringWidth(label+"...")>width)label=label.substring(0,label.length()-1);return label+"...";}
    public Dimension getPreferredScrollableViewportSize(){return new Dimension(850,350);}
    public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 32;}
    public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return Math.max(32,r.height-32);}
    public boolean getScrollableTracksViewportWidth(){return true;}
    public boolean getScrollableTracksViewportHeight(){return false;}
}
