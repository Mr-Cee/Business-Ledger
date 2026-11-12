package com.businessledger;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

/** Side-by-side income/expense columns, grouped and labeled once per month. */
final class MonthlyChart extends JPanel implements Scrollable {
    record Month(YearMonth date,BigDecimal income,BigDecimal expenses){BigDecimal net(){return income.subtract(expenses);}}
    private static final Color INCOME=new Color(45,137,92),EXPENSE=new Color(193,75,75),NET=new Color(55,104,168);
    private static final NumberFormat MONEY=NumberFormat.getCurrencyInstance(Locale.US);
    private List<Month> months=List.of();
    MonthlyChart(){
        setBackground(AppTheme.surface());setToolTipText("Monthly income and expenses");
        addComponentListener(new java.awt.event.ComponentAdapter(){private int width=-1;@Override public void componentResized(java.awt.event.ComponentEvent e){if(width!=getWidth()){width=getWidth();revalidate();}}});
    }
    void setMonths(Map<YearMonth,BigDecimal[]> values){months=values.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->new Month(e.getKey(),e.getValue()[0],e.getValue()[1])).toList();revalidate();repaint();}
    private static String compact(double amount){if(Math.abs(amount)<1000){NumberFormat dollars=NumberFormat.getCurrencyInstance(Locale.US);dollars.setMinimumFractionDigits(0);return dollars.format(amount);}NumberFormat format=NumberFormat.getCompactNumberInstance(Locale.US,NumberFormat.Style.SHORT);format.setMaximumFractionDigits(1);return (amount<0?"-$":"$")+format.format(Math.abs(amount));}
    private double[] scale(){
        double max=0;for(Month month:months)max=Math.max(max,Math.max(month.income().doubleValue(),month.expenses().doubleValue()));
        if(max==0)return new double[]{1,4};
        double raw=max/4,power=Math.pow(10,Math.floor(Math.log10(raw))),fraction=raw/power;
        double step=Math.max(.01,(fraction<=1?1:fraction<=2?2:fraction<=5?5:10)*power);
        return new double[]{step,Math.ceil(max/step)};
    }
    private int axisWidth(FontMetrics fm){double[] scale=scale();return Math.max(62,fm.stringWidth(compact(scale[0]*scale[1]))+18);}
    private int groups(int width){
        FontMetrics fm=getFontMetrics(getFont());int capacity=Math.max(1,(width-axisWidth(fm)-22)/Math.max(76,fm.stringWidth("Net: -$99.9K")+14));
        int bands=Math.max(1,(months.size()+capacity-1)/capacity);
        return Math.max(1,(months.size()+bands-1)/bands);
    }
    private int bands(int width){int per=groups(width);return Math.max(1,(months.size()+per-1)/per);}
    private int baseBandHeight(){return Math.max(235,getFontMetrics(getFont()).getHeight()*3+165);}
    private int headerHeight(){return getFontMetrics(getFont()).getHeight()+22;}
    private int bandHeight(){return Math.max(baseBandHeight(),(getHeight()-headerHeight())/bands(getWidth()));}
    @Override public Dimension getPreferredSize(){int width=getParent() instanceof JViewport v?v.getWidth():getWidth();if(width<=0)width=850;return new Dimension(width,headerHeight()+bands(width)*baseBandHeight());}
    public Dimension getPreferredScrollableViewportSize(){return new Dimension(700,340);}
    public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 36;}
    public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return Math.max(36,r.height-36);}
    public boolean getScrollableTracksViewportWidth(){return true;}
    public boolean getScrollableTracksViewportHeight(){return bands(getWidth())==1 && getParent()!=null && getParent().getHeight()>getPreferredSize().height;}
    @Override protected void paintComponent(Graphics graphics){
        setBackground(AppTheme.surface());super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();
        try{
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setFont(getFont());FontMetrics fm=g.getFontMetrics();
            if(months.isEmpty()){g.setColor(AppTheme.text());g.drawString("Choose dates and refresh to compare monthly income and expenses.",16,36);return;}
            int legendY=fm.getAscent()+10;g.setColor(INCOME);g.fillRoundRect(16,12,12,12,3,3);g.drawString("Income",35,legendY);
            int next=55+fm.stringWidth("Income");g.setColor(EXPENSE);g.fillRoundRect(next,12,12,12,3,3);g.drawString("Expenses",next+20,legendY);
            int left=axisWidth(fm),plotWidth=Math.max(1,getWidth()-left-22),per=groups(getWidth()),height=bandHeight();double slot=plotWidth/(double)per;
            double[] scale=scale();double ceiling=scale[0]*scale[1];
            for(int band=0;band<bands(getWidth());band++){
                int start=headerHeight()+band*height,top=start+12,bottom=start+height-fm.getHeight()*3-12,plotHeight=Math.max(1,bottom-top);
                if(band%2==1){g.setColor(AppTheme.band());g.fillRect(0,start,getWidth(),height);}
                g.setFont(getFont());
                for(int tick=0;tick<=(int)scale[1];tick++){
                    int y=bottom-(int)(tick*scale[0]/ceiling*plotHeight);g.setColor(AppTheme.grid());g.drawLine(left,y,left+plotWidth,y);
                    String label=compact(tick*scale[0]);g.setColor(AppTheme.muted());g.drawString(label,left-fm.stringWidth(label)-10,y+fm.getAscent()/2);
                }
                for(int group=0;group<per;group++){
                    int index=band*per+group;if(index>=months.size())break;Month month=months.get(index);int center=left+(int)((group+.5)*slot),barWidth=Math.max(7,Math.min(32,(int)(slot-24)/2));
                    int inHeight=(int)(month.income().doubleValue()/ceiling*plotHeight),outHeight=(int)(month.expenses().doubleValue()/ceiling*plotHeight);
                    if(month.income().signum()>0){g.setColor(INCOME);g.fillRoundRect(center-barWidth-3,bottom-Math.max(1,inHeight),barWidth,Math.max(1,inHeight),4,4);}
                    if(month.expenses().signum()>0){g.setColor(EXPENSE);g.fillRoundRect(center+3,bottom-Math.max(1,outHeight),barWidth,Math.max(1,outHeight),4,4);}
                    String label=month.date().format(DateTimeFormatter.ofPattern("MMM",Locale.getDefault()));g.setColor(AppTheme.text());g.setFont(getFont().deriveFont(Font.BOLD));g.drawString(label,center-g.getFontMetrics().stringWidth(label)/2,bottom+fm.getHeight());
                    String year=Integer.toString(month.date().getYear());g.setFont(getFont());g.setColor(AppTheme.muted());g.drawString(year,center-fm.stringWidth(year)/2,bottom+fm.getHeight()*2);
                    String net="Net: "+compact(month.net().doubleValue());
                    if(fm.stringWidth(net)>slot-6){while(fm.stringWidth(net+"…")>slot-6 && net.length()>1)net=net.substring(0,net.length()-1);net+="…";}
                    g.setColor(AppTheme.dark()?new Color(135,187,246):NET);g.drawString(net,center-fm.stringWidth(net)/2,bottom+fm.getHeight()*3);
                }
            }
        }finally{g.dispose();}
    }
    @Override public String getToolTipText(java.awt.event.MouseEvent e){
        if(months.isEmpty())return null;int per=groups(getWidth()),left=axisWidth(getFontMetrics(getFont())),plotWidth=Math.max(1,getWidth()-left-22);
        if(e.getY()<headerHeight() || e.getX()<left || e.getX()>=left+plotWidth)return null;
        int band=(e.getY()-headerHeight())/bandHeight(),group=(int)((e.getX()-left)*per/(double)plotWidth),index=band*per+group;
        if(index<0 || index>=months.size())return null;Month month=months.get(index);
        return month.date().format(DateTimeFormatter.ofPattern("MMMM uuuu",Locale.getDefault()))+" — Income: "+MONEY.format(month.income())+" | Expenses: "+MONEY.format(month.expenses())+" | Net: "+MONEY.format(month.net());
    }
}
