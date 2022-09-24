/*
 * Modification History:
 *    2011.02.07 Kalyptus 
 *    Note    : Ability to load even a 14 digit G Card Number, current is 12 digit.
 *    Solution: (temporary/permanet) use RESERVE1(4 byte) as the prefix holder.  
 *              first 2 bytes for gcard/second 2 bytes for client id
 *    - Updated the methods updatePSC, read, and write.
 *    - Created the method readres
 *    2011.03.22 Kalyptus
 *    - Updated toString(field, object) method
 *          - controls the lenth of input for each field.
 *          - added testing for SERIAL1, SERIAL2, SERIAL3, CLIENTID, CARD_NUMBER
 */

package org.rmj.gcard.base.misc;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import org.rmj.gdevice.SLE4428;

/**
 *
 * @author kalyptus
 */

// class use to read and write to the equipment
public class GCEncoder {
   private static SLE4428 sle = new SLE4428();
   public static boolean init(){
      return sle.init();
   }

   public static boolean connect(){
      return sle.connect();
   }

   public static boolean verifyPSC(String pin1, String pin2){
      int oldpin1 = ((String) readres(RESERVED3)).charAt(0);
      int oldpin2 = ((String) readres(RESERVED5)).charAt(0); 
      
      //kalyptus - 2015.08.15 01:55pm
      //perform the following testing to at least lessen 
      //pin verification failure
      //Note: Assume that cards has an initial pin of FFFF
      if(!(pin1.contentEquals("FF") || pin2.contentEquals("FF"))){
         //check first byte of pin 
         System.out.println("GCEncoder.verifyPSC(" + String.valueOf(oldpin1) + ", " + String.valueOf(oldpin2) + ") =>view pins...");
         System.out.println("GCEncoder.verifyPSC(" + pin1 + ", " + pin2 + ") =>view pins...");
         if(!(pin1.contentEquals(String.valueOf(oldpin1)) && pin2.contentEquals(String.valueOf(oldpin2)))){
            System.out.println("GCEncoder.verifyPSC(" + String.valueOf(oldpin1) + ", " + String.valueOf(oldpin2) + ") =>invalid pins...");
            System.out.println("GCEncoder.verifyPSC(" + pin1 + ", " + pin2 + ") =>invalid pins...");
            return false;
         }
      }
      
      System.out.println("GCEncoder.verifyPSC(" + pin1 + ", " + pin2 + ")");
      return sle.verifyPSC(pin1, pin2);
   }

   public static boolean updatePSC(String newpin1, String newpin2){
      //Disable writing to RESERVED1 since it will be used for prefix
//      writeres(RESERVED1, getXByte(DATA_LEN[RESERVED1-1]));
      writeres(RESERVED2, getXByte(DATA_LEN[RESERVED2-1]));
      byte pin1[] = {(byte)Integer.parseInt(newpin1)};
      writeres(RESERVED3, pin1);
      writeres(RESERVED4, getXByte(DATA_LEN[RESERVED4-1]));
      byte pin2[] = {(byte)Integer.parseInt(newpin2)};
      writeres(RESERVED5, pin2);
      writeres(RESERVED6, getXByte(DATA_LEN[RESERVED6-1]));
      return sle.updatePSC(newpin1, newpin2);
   }

   private static boolean writeres(int field, byte[] object){
      //encode them
       
      System.out.println("WriteRes - Before Encryption: - Field: " + field + "»Value: " + object.toString());
       
      byte[] newdata = object;
      //perform a simple encryption here
      for(int x=0, y=0; x<newdata.length-1;x++){
         y += (y==SALT_LICK.length ? -y : 1);
         newdata[x] = (byte) (newdata[x] + SALT_LICK[y]);
      }

      System.out.println("WriteRes - After Encryption: - Field: " + field + "»Value: " + newdata.toString());
      
      return sle.write(getAddress(field), newdata);
   }

   //This method is created to enable reading of the reserve area
   private static Object readres(int field){
      byte[] newdata = sle.read(getAddress(field), DATA_LEN[field-1]);
      //perform a simple decryption here
      for(int x=0, y=0; x<newdata.length-1;x++){
         y += (y==SALT_LICK.length ? -y : 1);
         newdata[x] = (byte) (newdata[x] - SALT_LICK[y]);
      }

      String ret = new String(newdata);

      return ret;
   }
   
   public static boolean disconnect(){
      return sle.disconnect();
   }

   public static Object read(int field){
      psErrMessg = "";
      if(field < RESERVED1 && field > G_CARD_LAST_FIELD){
         psErrMessg = "Field is a reserved area and can't be read.";
         return null;
      }

      byte[] newdata = sle.read(getAddress(field), DATA_LEN[field-1]);
      //perform a simple decryption here
      for(int x=0, y=0; x<newdata.length-1;x++){
         y += (y==SALT_LICK.length ? -y : 1);
         newdata[x] = (byte) (newdata[x] - SALT_LICK[y]);
      }

      String ret = new String(newdata);
      
      //get the prefix
      if(field == CLIENT_ID || field == CARD_NUMBER) {
          String prefix = (String) readres(RESERVED1);
          
          System.out.println("Prefix Value: " + prefix);
          
          if(prefix.length() <= 3){
              prefix = "XXXX";
          }
          
          String no = prefix.substring(0, 2);  
          String id = prefix.substring(2, 4);
          
          if(field == CLIENT_ID){
//              if(prefix.startsWith("C") || prefix.startsWith("M"))  
              if(id.matches("C[0-9]") || id.matches("M[0-9]")){  
                 System.out.println("ID:" + id);
                 ret = prefix.substring(2, 4) + ret;
              }    
              else
                  ret = "M0" + ret;
          } 
          else{
//              if(no.matches("M[0-9]")){  
             //kalyptus - 2015.08.03
             //just use start with
              if(prefix.startsWith("M")){  
                 System.out.println("No:" + no);
                 ret = prefix.substring(1, 2) + ret;
              }   
              else
                 ret = "0" + ret;
          }
      }    
      else if (field == SERIAL1 || field == SERIAL2 || field == SERIAL3){
         ret = "M0" + ret;
      }
      
      if(field == RESERVED1 ||
         field == RESERVED2 ||
         field == RESERVED3 ||
         field == RESERVED4 ||
         field == RESERVED5 ||
         field == RESERVED6){

         return ret;
      }
      else if(field == BIRTH_DATE ||
              field == CARD_EXPIRY ||
              field == POINTS_EXPIRY){
         if(ret.equalsIgnoreCase("01011900"))
            return null;
         else
            return toDate(ret, "yyyyMMdd");
      }
      else if(field == POINTS ||
              field == REMAINING_MC){
//         System.out.println("Before parsing: Read - Field: " + field + "»Value: " + ret);
//         System.out.println("After parsing: Read - Field: " + field + "»Value: " + Long.parseLong(ret));
         if(ret.isEmpty())
            return (long)0;
         else
            return getLong(ret);
      }
      else{
         return ret;
      }
   }
   
    private static long getLong(String value){
       if(!isNumeric(value)) return 0;
       return Double.valueOf(value).longValue();
    }   

   private static boolean isNumeric(String str)
   {
     return str.matches("-?\\d+(\\.\\d+)?");  //match a number with optional '-' and decimal.
   }       
    
   public static boolean write(int field, Object object){
      psErrMessg = "";

      if(field == RESERVED1 || 
         field == RESERVED2 || 
         field == RESERVED3 ||
         field == RESERVED4 ||
         field == RESERVED5 ||
         field == RESERVED6){
         //TODO: inform that field is a reserved area
         psErrMessg = "Field is a reserved area and can't be written.";
         return false;
      }
      
      if(field < RESERVED1 && field > G_CARD_LAST_FIELD){
         psErrMessg = "Unknown Field!";
         return false;
      }

      String data = toString(field, object);
      System.out.println("Write: Before Split - Field: " + field + "»Value: " + data);
        
      //test if field pass is already using the new structure
      if((field == CLIENT_ID && data.length() == 12) || (field == CARD_NUMBER && data.length() == 13)) {
          String prefix = (String) readres(RESERVED1);
         
          //is card still empty
          if(prefix.length() <= 2){
              prefix = "XXXX";
          }
          
          String no = prefix.substring(0, 2);  
          String id = prefix.substring(2, 4);
          
          System.out.println("Field : " + (field == CLIENT_ID ? "CLIENT ID" : "CARD NMBR"));
          System.out.println("No : " + no);
          System.out.println("Id : " + id);
          
          //get the first 2 digit of the CARD NUMBER
          if(field == CLIENT_ID){
              id = data.substring(0,2);
              data = data.substring(2);
          }    
          else{
              no = "M" + data.substring(0,1);
              data = data.substring(1);
          }

          prefix = no + id;
          
          System.out.println("Field : " + (field == CLIENT_ID ? "CLIENT ID" : "CARD NMBR" + " PREFIX: " + prefix));
          System.out.println("Data : " + data);
          
          //write the prefixes to the RESERVE1 field
          writeres(RESERVED1, prefix.getBytes());
      }
      else if ((field == SERIAL1 || field == SERIAL2 || field == SERIAL3) && data.length() == 12 ){
        data = data.substring(2);
      }      
      
      byte[] newdata = data.getBytes();

      System.out.println("Write - Before Encryption: - Field: " + field + "»Value: " + newdata);
           
      //perform a simple encryption here
      for(int x=0, y=0; x<newdata.length-1;x++){
         y += (y==SALT_LICK.length ? -y : 1);
         newdata[x] = (byte) (newdata[x] + SALT_LICK[y]);
      }

      System.out.println("Write - After Encryption: - Field: " + field + "»Value: " + newdata);

      return sle.write(getAddress(field), newdata);
   }

   public static String getErrMessage(){
      if(psErrMessg.isEmpty())
         return psErrMessg;
      else
         return sle.getErrMessage();
   }

   private static int getAddress(int field){
      int ret = 0;
      field--;
      if((field < 0) || (field > (G_CARD_LAST_FIELD-2)))
         return -0;
      else
         for(int n = 0;n<field;n++)
            ret += DATA_LEN[n];

      return G_CARD_BEG_ADD + ret;
   }

   //converter of the different fields to string
   private static String toString(int field, Object object){
      if(field == BIRTH_DATE ||
         field == CARD_EXPIRY ||
         field == POINTS_EXPIRY){
         return toString((Date) object, DATA_LEN[field-1]);
      }
      else if(field == POINTS ||
              field == REMAINING_MC){
         return toString((Long) object, DATA_LEN[field-1]);
      }
      else if(field == SERIAL1 ||
              field == SERIAL2 || 
              field == SERIAL3 || 
              field == CLIENT_ID){
         return toString((String) object, DATA_LEN[field-1] + 2);
      }
      else if(field == CARD_NUMBER){
         return toString((String) object, DATA_LEN[field-1] + 1);
      }
      else{
         return toString((String) object, DATA_LEN[field-1]);
      }
   }

   private static String toString(String data, int len){
      if(data.length() > len){
         return data.substring(0, len);
      }
      return String.format("%-" + len + "s", data);
   }
   private static String toString(Date data, int len){
      if(data == null){
         return "01011900";
      }
      return dateFormat(data, "yyyyMMdd");
   }
   
   private static String toString(Long data, int len){
      return String.format("%0" + len + "d", data);
   }
   
   //List of methods to be use by the different converters
   private static String dateFormat(Object date, String format){
        SimpleDateFormat sf = new SimpleDateFormat(format);
        if ( date instanceof Timestamp )
           return sf.format((Date)date);
        else if ( date instanceof Date )
           return sf.format(date);
        else if ( date instanceof Calendar ){
           Calendar loDate = (Calendar) date;
           return sf.format(loDate.getTime());
        }
        else
           return null;
    }

    private static Date toDate(String date, String format){
       Date loDate = null;
       try{
          //Be sure to follow the format specified
          SimpleDateFormat sf = new SimpleDateFormat(format);
          loDate = sf.parse(date);
       }
       catch(ParseException ex){
            ex.printStackTrace();
            psErrMessg = ex.getMessage();
       }
       return loDate;
    }
    
   private static byte[] getXByte(int len){
      Random ran = new Random();
      byte data[] = new byte[len];
      for(int x=0;x<len-1;x++)
         data[x] = (byte) ran.nextInt(99);

      return data;
   }

   private static byte DATA_LEN[] = {
                        0x04, 0x03, 0x01,
                        0x0C, 0x01, 0x0A,
                        0x08, 0x08, 0x0A,
                        0x08, 0x04, 0x0A,
                        0x0A, 0x0A, 0x03,
                        0x02, 0x01, 0x01};

   private static byte SALT_LICK[] = {
                        0x04, 0x03, 0x01,
                        0x0C, 0x01, 0x0A,
                        0x08, 0x08, 0x0A,
                        0x08, 0x04, 0x0A,
                        0x0A, 0x0A, 0x03,
                        0x02, 0x01, 0x01};

   public static byte RESERVED1 = 0x01; //1234 => 01 = CARD NO; 34 = CLIENT ID 
   public static byte RESERVED2 = 0x02;
   public static byte RESERVED3 = 0x03; //PIN #1
   public static byte CARD_NUMBER = 0x04;
   public static byte CARD_TYPE = 0x05;
   public static byte CLIENT_ID = 0x06;
   public static byte BIRTH_DATE = 0x07;
   public static byte CARD_EXPIRY = 0x08;
   public static byte POINTS = 0x09;
   public static byte POINTS_EXPIRY = 0x0A;
   public static byte TOWN_ID = 0x0B;
   public static byte SERIAL1 = 0x0C;
   public static byte SERIAL2 = 0x0D;
   public static byte SERIAL3 = 0x0E;
   public static byte REMAINING_MC = 0x0F;
   public static byte RESERVED4 = 0x10;
   public static byte RESERVED5 = 0x11; //PIN #2
   public static byte RESERVED6 = 0x12;

   private static String psErrMessg = "";
   private static final int G_CARD_BEG_ADD = 0x19;
   private static final int G_CARD_LAST_FIELD = 0x12;  //BE SURE TO REFER TO LAST FIELD
}
 
