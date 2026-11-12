package com.businessledger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Arc2D;
import java.math.*;
import java.sql.*;
import java.time.LocalDate;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

/** Top expense payees; the remaining payees share one slice so the whole pie stays meaningful. */
final class PayeePieChart extends JPanel implements Scrollable {
    record Slice(String label,BigDecimal amount,long count){}
    private static final Color[] COLORS={new Color(55,114,184),new Color(46,145,104),new Color(219,143,45),new Color(144,99,184),new Color(203,91,129),new Color(39,151,166),new Color(167,158,48),new Color(198,82,70),new Color(125,136,151)};
    private static final NumberFormat MONEY=NumberFormat.getCurrencyInstance(Locale.US);
    private List<Slice> slices=List.of();
    private BigDecimal total=BigDecimal.ZERO;
    private int hovered=-1;
    PayeePieChart(){
        setBackground(AppTheme.surface());setToolTipText("Hover over a payee slice or legend row.");
        addMouseMotionListener(new MouseMotionAdapter(){@Override public void mouseMoved(MouseEvent e){int selected=sliceAt(e.getPoint());if(hovered!=selected){hovered=selected;repaint();}}});
        addMouseListener(new MouseAdapter(){@Override public void mouseExited(MouseEvent e){hovered=-1;repaint();}});
        addComponentListener(new ComponentAdapter(){private int width=-1;@Override public void componentResized(ComponentEvent e){if(width!=getWidth()){width=getWidth();revalidate();}}});
    }
    static List<Slice> load(Connection db,LocalDate start,LocalDate end)throws SQLException{
        if(start.isAfter(end))throw new IllegalArgumentException("From date must be on or before the through date.");
        List<Slice> all=new ArrayList<>();
        try(PreparedStatement s=db.prepareStatement("SELECT trim(party),SUM(amount),COUNT(*) FROM transactions WHERE kind='Expense' AND entry_date BETWEEN ? AND ? GROUP BY trim(party) ORDER BY SUM(amount) DESC,trim(party)")){
            s.setObject(1,start);s.setObject(2,end);try(ResultSet r=s.executeQuery()){while(r.next()){
                String name=r.getString(1);all.add(new Slice(name==null||name.isBlank()?"Unspecified payee":name,r.getBigDecimal(2),r.getLong(3)));
            }}
        }
        if(all.size()<=8)return List.copyOf(all);
        List<Slice> top=new ArrayList<>(all.subList(0,8));BigDecimal other=BigDecimal.ZERO;long count=0;
        for(Slice slice:all.subList(8,all.size())){other=other.add(slice.amount());count+=slice.count();}
        top.add(new Slice("Other payees ("+(all.size()-8)+")",other,count));return List.copyOf(top);
    }
    void setSlices(List<Slice> values){slices=values.stream().filter(s->s.amount().signum()>0).toList();total=slices.stream().map(Slice::amount).reduce(BigDecimal.ZERO,BigDecimal::add);hovered=-1;revalidate();repaint();}
    BigDecimal percent(Slice slice){return total.signum()==0?BigDecimal.ZERO:slice.amount().multiply(new BigDecimal("100")).divide(total,2,RoundingMode.HALF_UP);}
    private record Layout(Rectangle pie,int legendX,int legendY,int legendWidth,int rowHeight){}
    private Layout arrangement(int width){
        boolean wide=width>=760;int diameter=wide?Math.min(360,Math.max(180,(int)(width*.43)-36)):Math.max(80,Math.min(320,width-48));
        int x=wide?24:Math.max(12,(width-diameter)/2),y=62;
        int legendX=wide?x+diameter+32:24,legendY=wide?58:y+diameter+32;
        return new Layout(new Rectangle(x,y,diameter,diameter),legendX,legendY,Math.max(1,width-legendX-24),getFontMetrics(getFont()).getHeight()*2+12);
    }
    @Override public Dimension getPreferredSize(){
        int width=getParent() instanceof JViewport v?v.getWidth():getWidth();if(width<=0)width=900;Layout a=arrangement(width);
        return new Dimension(width,Math.max(a.pie().y+a.pie().height+30,a.legendY()+slices.size()*a.rowHeight()+24));
    }
    private int sliceAt(Point point){
        if(slices.isEmpty())return -1;Layout a=arrangement(getWidth());
        if(point.x>=a.legendX()&&point.x<a.legendX()+a.legendWidth()&&point.y>=a.legendY()){
            int index=(point.y-a.legendY())/a.rowHeight();if(index<slices.size())return index;
        }
        Rectangle p=a.pie();double dx=point.x-p.getCenterX(),dy=point.y-p.getCenterY();
        if(dx*dx+dy*dy>p.width*p.width/4.0)return -1;
        double angle=(Math.toDegrees(Math.atan2(dy,dx))+90+360)%360,cumulative=0;
        for(int i=0;i<slices.size();i++){cumulative+=slices.get(i).amount().doubleValue()/total.doubleValue()*360;if(angle<cumulative||i==slices.size()-1)return i;}return -1;
    }
    @Override public String getToolTipText(MouseEvent e){
        int index=sliceAt(e.getPoint());if(index<0)return null;Slice slice=slices.get(index);
        return "Payee: "+slice.label()+" | Spent: "+MONEY.format(slice.amount())+" | Share of expenses: "+percent(slice).toPlainString()+"% | Expenses: "+slice.count();
    }
    @Override protected void paintComponent(Graphics graphics){
        setBackground(AppTheme.surface());super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();
        try{
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setFont(getFont());
            if(slices.isEmpty()){g.setColor(AppTheme.text());g.drawString("No expenses in this date range.",24,42);return;}
            Layout a=arrangement(getWidth());Rectangle p=a.pie();g.setColor(AppTheme.text());g.setFont(getFont().deriveFont(Font.BOLD));
            String title="Total expenses: "+MONEY.format(total);g.drawString(title,Math.max(16,(int)p.getCenterX()-g.getFontMetrics().stringWidth(title)/2),30);
            double angle=90;
            for(int i=0;i<slices.size();i++){
                Slice slice=slices.get(i);double extent=slice.amount().doubleValue()/total.doubleValue()*360;Color color=COLORS[i%COLORS.length];
                Arc2D arc=new Arc2D.Double(p.x,p.y,p.width,p.height,angle,-extent,Arc2D.PIE);
                g.setColor(i==hovered?color.brighter():color);g.fill(arc);g.setStroke(new BasicStroke(i==hovered?3:2));g.setColor(AppTheme.surface());g.draw(arc);
                if(extent>=24){
                    g.setFont(getFont().deriveFont(Font.BOLD));
                    double mid=Math.toRadians(angle-extent/2),radius=p.width*.32;String share=percent(slice).setScale(1,RoundingMode.HALF_UP).toPlainString()+"%";
                    g.setColor((color.getRed()*.299+color.getGreen()*.587+color.getBlue()*.114)>155?Color.BLACK:Color.WHITE);
                    g.drawString(share,(int)(p.getCenterX()+Math.cos(mid)*radius-g.getFontMetrics().stringWidth(share)/2),(int)(p.getCenterY()-Math.sin(mid)*radius+g.getFontMetrics().getAscent()/2));
                }angle-=extent;
                int y=a.legendY()+i*a.rowHeight();
                if(i==hovered){g.setColor(AppTheme.band());g.fillRoundRect(a.legendX()-8,y-5,a.legendWidth()+8,a.rowHeight()-2,8,8);}
                g.setColor(color);g.fillRoundRect(a.legendX(),y+2,13,13,3,3);
                g.setFont(getFont().deriveFont(Font.BOLD));g.setColor(AppTheme.text());String label=slice.label();int available=Math.max(1,a.legendWidth()-24);
                if(g.getFontMetrics().stringWidth(label)>available){while(!label.isEmpty()&&g.getFontMetrics().stringWidth(label+"...")>available)label=label.substring(0,label.length()-1);label+="...";}
                g.drawString(label,a.legendX()+23,y+g.getFontMetrics().getAscent());g.setFont(getFont());g.setColor(AppTheme.muted());
                g.drawString(MONEY.format(slice.amount())+"  |  "+percent(slice).toPlainString()+"%  |  "+slice.count()+" expenses",a.legendX()+23,y+g.getFontMetrics().getHeight()*2);
            }
        }finally{g.dispose();}
    }
    public Dimension getPreferredScrollableViewportSize(){return new Dimension(850,400);}
    public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 30;}
    public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return Math.max(30,r.height-30);}
    public boolean getScrollableTracksViewportWidth(){return true;}
    public boolean getScrollableTracksViewportHeight(){return getParent()!=null&&getParent().getHeight()>getPreferredSize().height;}
}
