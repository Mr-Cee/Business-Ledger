package com.businessledger;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Form captions must retain painting room after wrapping, theme and font changes. */
public final class FieldLabelCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void layout(Container root){root.doLayout();for(Component c:root.getComponents())if(c instanceof Container child)layout(child);}
    public static void main(String[] args)throws Exception{
        Files.createDirectories(Path.of("target/label-spacing"));
        SwingUtilities.invokeAndWait(()->{try{
            for(AppTheme.Style style:new AppTheme.Style[]{AppTheme.Style.OCEAN,AppTheme.Style.MIDNIGHT}){
                AppTheme.apply(style);
                WrapPanel form=new WrapPanel();
                form.addField("Date (YYYY-MM-DD)",new JTextField("2026-11-12",10));
                form.addField("Type",new JComboBox<>(new String[]{"Expense","Income"}));
                form.addField("Category",new JComboBox<>(new String[]{"Advertising","Supplies"}));
                form.addField("Amount ($)",new JTextField(8));
                form.addField("Payee / customer",new JTextField(12));
                form.addField("Notes",new JTextField(15));
                form.addField("Account",new JComboBox<>(new String[]{"Expenses","Checking"}));
                form.addField("Search ledger",new JTextField(20));
                JPanel page=new JPanel(new BorderLayout());page.add(form,BorderLayout.NORTH);
                for(int fontSize:new int[]{14,18})for(int width:new int[]{650,1080}){
                    for(Component c:form.getComponents()){
                        JLabel label=(JLabel)((JPanel)c).getComponent(0);
                        label.setFont(label.getFont().deriveFont((float)fontSize));
                    }
                    page.setSize(width,400);for(int pass=0;pass<4;pass++)layout(page);
                    int lastBottom=0;
                    for(Component c:form.getComponents()){
                        JPanel pair=(JPanel)c;JLabel label=(JLabel)pair.getComponent(0);Component input=pair.getComponent(1);
                        Insets in=label.getInsets();int available=label.getWidth()-in.left-in.right;
                        check(available>=label.getFontMetrics(label.getFont()).stringWidth(label.getText())+8,"Caption lacks painting room: "+label.getText());
                        check(label.getLabelFor()==input,"Caption is associated with its input");
                        check(label.getX()+label.getWidth()<=input.getX(),"Caption overlaps input");
                        check(input.getWidth()>=input.getPreferredSize().width,"Wrapping shrank an input");
                        check(pair.getX()>=0&&pair.getX()+pair.getWidth()<=width,"Field exceeds available row width");
                        lastBottom=Math.max(lastBottom,pair.getY()+pair.getHeight());
                    }
                    check(form.getHeight()>=lastBottom,"Wrapped row is clipped");
                    BufferedImage image=new BufferedImage(width,400,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();page.printAll(graphics);graphics.dispose();
                    ImageIO.write(image,"png",Path.of("target/label-spacing/"+style.name()+"-"+width+"-"+fontSize+".png").toFile());
                }
            }
            System.out.println("Field captions retain painting room, input spacing, accessible labels and complete wrapped rows at narrow/wide widths, larger fonts and light/dark themes.");
        }catch(Exception ex){throw new RuntimeException(ex);}});
    }
}
