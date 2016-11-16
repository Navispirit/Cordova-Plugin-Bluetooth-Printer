package com.ru.cordova.printer.bluetooth;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Hashtable;
import java.util.Set;
import java.util.UUID;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Bitmap.Config;
import android.util.Xml.Encoding;
import android.util.Base64;
import java.util.BitSet; 





public class BluetoothPrinter extends CordovaPlugin {
	private static final String LOG_TAG = "BluetoothPrinter";
	BluetoothAdapter mBluetoothAdapter;
	BluetoothSocket mmSocket;
	BluetoothDevice mmDevice;
	OutputStream mmOutputStream;
	InputStream mmInputStream;
	Thread workerThread;
	byte[] readBuffer;
	int readBufferPosition;
	int counter;
	BitSet dots;
	volatile	 boolean stopWorker;

	Bitmap bitmap;
	
public static class PrinterCommands {

	public static final byte[] INIT = {27, 64};
	public static byte[] FEED_LINE = {10};

	public static byte[] SELECT_FONT_A = {27, 33, 0};

	public static byte[] SET_BAR_CODE_HEIGHT = {29, 104, 100};
	public static byte[] PRINT_BAR_CODE_1 = {29, 107, 2};
	public static byte[] SEND_NULL_BYTE = {0x00};

	public static byte[] SELECT_PRINT_SHEET = {0x1B, 0x63, 0x30, 0x02};
	public static byte[] FEED_PAPER_AND_CUT = {0x1D, 0x56, 66, 0x00};

	public static byte[] SELECT_CYRILLIC_CHARACTER_CODE_TABLE = {0x1B, 0x74, 0x11};

	//public static byte[] SELECT_BIT_IMAGE_MODE = {0x1B, 0x2A, 33, -128, 0};
	//public static byte[] SELECT_BIT_IMAGE_MODE = {27, 42, 33, -128, 0};
	public static byte[] SELECT_BIT_IMAGE_MODE = {27, 42, 33};

	public static byte[] SET_LINE_SPACING_24 = {0x1B, 0x33, 24};
	public static byte[] SET_LINE_SPACING_30 = {0x1B, 0x33, 30};

	public static byte[] TRANSMIT_DLE_PRINTER_STATUS = {0x10, 0x04, 0x01};
	public static byte[] TRANSMIT_DLE_OFFLINE_PRINTER_STATUS = {0x10, 0x04, 0x02};
	public static byte[] TRANSMIT_DLE_ERROR_STATUS = {0x10, 0x04, 0x03};
	public static byte[] TRANSMIT_DLE_ROLL_PAPER_SENSOR_STATUS = {0x10, 0x04, 0x04};



}



	
	

	public BluetoothPrinter() {}
	
	

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		if (action.equals("list")) {
			listBT(callbackContext);
			return true;
		} else if (action.equals("connect")) {
			String name = args.getString(0);
			if (findBT(callbackContext, name)) {
				try {
					connectBT(callbackContext);
				} catch (IOException e) {
					Log.e(LOG_TAG, e.getMessage());
					e.printStackTrace();
				}
			} else {
				callbackContext.error("Bluetooth Device Not Found: " + name);
			}
			return true;
		} else if (action.equals("disconnect")) {
            try {
                disconnectBT(callbackContext);
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        }
        else if (action.equals("print")) {
			try {
				String msg = args.getString(0);
				print(callbackContext, msg);
			} catch (IOException e) {
				Log.e(LOG_TAG, e.getMessage());
				e.printStackTrace();
			}
			return true;
		}
        else if (action.equals("printPOSCommand")) {
			try {
				String msg = args.getString(0);
                printPOSCommand(callbackContext, hexStringToBytes(msg));
			} catch (IOException e) {
				Log.e(LOG_TAG, e.getMessage());
				e.printStackTrace();
			}
			return true;
		}
		else if (action.equals("printText")) {
            try {
                String msg = args.getString(0);
                printText(callbackContext, msg);
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        }
        else if (action.equals("printQrcode")) {
            try {
                String msg = args.getString(0);
                printQrcode(callbackContext, msg);
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        }
         else if (action.equals("printQR")) {
            try {
                String msg = args.getString(0);
                printQRM(callbackContext, msg);
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        }
        else if (action.equals("printImage")) {
            try {
                String msg = args.getString(0);
                printImage(callbackContext, msg);
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        }
		return false;
	}

    //This will return the array list of paired bluetooth printers
	void listBT(CallbackContext callbackContext) {
		BluetoothAdapter mBluetoothAdapter = null;
		String errMsg = null;
		try {
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			if (mBluetoothAdapter == null) {
				errMsg = "No bluetooth adapter available";
				Log.e(LOG_TAG, errMsg);
				callbackContext.error(errMsg);
				return;
			}
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				this.cordova.getActivity().startActivityForResult(enableBluetooth, 0);
			}
			Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
			if (pairedDevices.size() > 0) {
				JSONArray json = new JSONArray();
				for (BluetoothDevice device : pairedDevices) {
					/*
					Hashtable map = new Hashtable();
					map.put("type", device.getType());
					map.put("address", device.getAddress());
					map.put("name", device.getName());
					JSONObject jObj = new JSONObject(map);
					*/
					json.put(device.getName());
				}
				callbackContext.success(json);
			} else {
				callbackContext.error("No Bluetooth Device Found");
			}
			//Log.d(LOG_TAG, "Bluetooth Device Found: " + mmDevice.getName());
		} catch (Exception e) {
			errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
	}

	// This will find a bluetooth printer device
	boolean findBT(CallbackContext callbackContext, String name) {
		try {
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			if (mBluetoothAdapter == null) {
				Log.e(LOG_TAG, "No bluetooth adapter available");
			}
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				this.cordova.getActivity().startActivityForResult(enableBluetooth, 0);
			}
			Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
			if (pairedDevices.size() > 0) {
				for (BluetoothDevice device : pairedDevices) {
					if (device.getName().equalsIgnoreCase(name)) {
						mmDevice = device;
						return true;
					}
				}
			}
			Log.d(LOG_TAG, "Bluetooth Device Found: " + mmDevice.getName());
		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}

	// Tries to open a connection to the bluetooth printer device
	boolean connectBT(CallbackContext callbackContext) throws IOException {
		try {
			// Standard SerialPortService ID
			UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
			mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
			mmSocket.connect();
			mmOutputStream = mmSocket.getOutputStream();
			mmInputStream = mmSocket.getInputStream();
			beginListenForData();
			//Log.d(LOG_TAG, "Bluetooth Opened: " + mmDevice.getName());
			callbackContext.success("Bluetooth Opened: " + mmDevice.getName());
			return true;
		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}

	// After opening a connection to bluetooth printer device,
	// we have to listen and check if a data were sent to be printed.
	void beginListenForData() {
		try {
			final Handler handler = new Handler();
			// This is the ASCII code for a newline character
			final byte delimiter = 10;
			stopWorker = false;
			readBufferPosition = 0;
			readBuffer = new byte[1024];
			workerThread = new Thread(new Runnable() {
				public void run() {
					while (!Thread.currentThread().isInterrupted() && !stopWorker) {
						try {
							int bytesAvailable = mmInputStream.available();
							if (bytesAvailable > 0) {
								byte[] packetBytes = new byte[bytesAvailable];
								mmInputStream.read(packetBytes);
								for (int i = 0; i < bytesAvailable; i++) {
									byte b = packetBytes[i];
									if (b == delimiter) {
										byte[] encodedBytes = new byte[readBufferPosition];
										System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
										/*
										final String data = new String(encodedBytes, "US-ASCII");
										readBufferPosition = 0;
										handler.post(new Runnable() {
											public void run() {
												myLabel.setText(data);
											}
										});
                                        */
									} else {
										readBuffer[readBufferPosition++] = b;
									}
								}
							}
						} catch (IOException ex) {
							stopWorker = true;
						}
					}
				}
			});
			workerThread.start();
		} catch (NullPointerException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	//This will send data to bluetooth printer
	boolean printText(CallbackContext callbackContext, String msg) throws IOException {
		try {

		
			//mmOutputStream.write(PrinterCommands.INIT);
			mmOutputStream.write(PrinterCommands.FEED_LINE);
			mmOutputStream.write(msg.getBytes());
			
			// tell the user data were sent
			//Log.d(LOG_TAG, "Data Sent");
			callbackContext.success("Data Sent:"+msg);
			return true;

		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}

		boolean printCyrillic(CallbackContext callbackContext, String msg) throws IOException {
		try {

			mmOutputStream.write(PrinterCommands.INIT);
		
			mmOutputStream.write(new byte[] { 0x1B, 0x74, 9 });
			
			mmOutputStream.write(msg.getBytes("cp866"));
			
			
			//mmOutputStream.write(getText(msg));
			
			mmOutputStream.write(PrinterCommands.FEED_LINE);
			
			// tell the user data were sent
			//Log.d(LOG_TAG, "Data Sent");
			callbackContext.success("Data Sent:"+msg);
			return true;

		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}

	//This will send data to bluetooth printer
    boolean print(CallbackContext callbackContext, String msg) throws IOException {
        try {

           // final String encodedString = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAGQAAABkCAYAAABw4pVUAAAEtElEQ…zCvZoaQ8iElVSITGGbsVL94lVXay8V0vxnOO92cXzl7x9QMIcW03lItQAAAABJRU5ErkJggg==";
            final String encodedString = msg;
            final String pureBase64Encoded = encodedString.substring(encodedString.indexOf(",")  + 1);

            final byte[] decodedBytes = Base64.decode(pureBase64Encoded, Base64.DEFAULT);

            Bitmap decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

            bitmap = decodedBitmap;

            int mWidth = bitmap.getWidth();
            int mHeight = bitmap.getHeight();
            //bitmap=resizeImage(bitmap, imageWidth * 8, mHeight);
            // coment
            bitmap=resizeImage(bitmap, 48 * 8, mHeight);


            byte[]  bt =getBitmapData(bitmap);

           // bitmap.recycle();

            mmOutputStream.write(bt);

            // tell the user data were sent
            //Log.d(LOG_TAG, "Data Sent");
            callbackContext.success("Data Sent");
            return true;


        } catch (Exception e) {
            String errMsg = e.getMessage();
            Log.e(LOG_TAG, errMsg);
            e.printStackTrace();
            callbackContext.error(errMsg);
        }
        return false;
    }

    boolean printImage(CallbackContext callbackContext, String msg) throws IOException {
    	try {
    	
    	final String encodedString = msg;
    	final String pureBase64Encoded = encodedString.substring(encodedString.indexOf(",")  + 1);
    	final byte[] decodedBytes = Base64.decode(pureBase64Encoded, Base64.DEFAULT);
    	 Bitmap bmp = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
    	 //resizeImage(bmp);
        
       	convertBitmap(bmp);

       	 // Set the line spacing at 24 (we'll print 24 dots high)
        mmOutputStream.write(PrinterCommands.SET_LINE_SPACING_24);
       	
        int offset = 0;
        




        while (offset < bmp.getHeight()) {
            mmOutputStream.write(PrinterCommands.SELECT_BIT_IMAGE_MODE);
            mmOutputStream.write(new byte[]{(byte)(0x00ff & bmp.getWidth()),(byte)((0xff00 & bmp.getWidth()) >> 8)});
            for (int x = 0; x < bmp.getWidth(); ++x) {

                for (int k = 0; k < 3; ++k) {
                	 byte slice = 0;
                    for (int b = 0; b < 8; ++b) {
                        int y = (((offset / 8) + k) * 8) + b;
                        int i = (y * bmp.getWidth()) + x;
                        boolean v = false;
                        if (i < dots.length()) {
                            v = dots.get(i);
                        }
                        slice |= (byte) ((v ? 1 : 0) << (7 - b));
                    }
                    mmOutputStream.write(slice);
                }
            }
            offset += 24;
            
            mmOutputStream.write(PrinterCommands.FEED_LINE);
            //mmOutputStream.write(PrinterCommands.FEED_LINE);          
           // mmOutputStream.write(PrinterCommands.FEED_LINE);
            //mmOutputStream.write(PrinterCommands.FEED_LINE);
            //mmOutputStream.write(PrinterCommands.FEED_LINE);
            //mmOutputStream.write(PrinterCommands.FEED_LINE);
            
        }
        mmOutputStream.write(PrinterCommands.SET_LINE_SPACING_30);
        callbackContext.success("Data Sent");
            return true;

        }catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error("print_img error:"+errMsg);
		}
		return false;
}

public String convertBitmap(Bitmap inputBitmap) {

    int mWidth = inputBitmap.getWidth();
    int mHeight = inputBitmap.getHeight();

    convertArgbToGrayscale(inputBitmap, mWidth, mHeight);
    String mStatus = "ok";
    return mStatus;

}

private void convertArgbToGrayscale(Bitmap bmpOriginal, int width, int height) {
    int pixel;
    int k = 0;
    int B = 0, G = 0, R = 0;
    dots = new BitSet();
    try {

        for (int x = 0; x < height; x++) {
            for (int y = 0; y < width; y++) {
                // get one pixel color
                pixel = bmpOriginal.getPixel(y, x);

                // retrieve color of all channels
                R = Color.red(pixel);
                G = Color.green(pixel);
                B = Color.blue(pixel);
                // take conversion up to one single value by calculating
                // pixel intensity.
                R = G = B = (int) (0.299 * R + 0.587 * G + 0.114 * B);
                // set bit into bitset, by calculating the pixel's luma
                if (R < 55) {                       
                    dots.set(k);//this is the bitset that i'm printing
                }
                k++;

            }


        }


    } catch (Exception e) {
        // TODO: handle exception
        Log.e(LOG_TAG, e.toString());
    }
}


    boolean printPOSCommand(CallbackContext callbackContext, byte[] buffer) throws IOException {
        try {
            mmOutputStream.write(buffer);
            // tell the user data were sent
			Log.d(LOG_TAG, "Data Sent");
            callbackContext.success("Data Sent");
            return true;
        } catch (Exception e) {
            String errMsg = e.getMessage();
            Log.e(LOG_TAG, errMsg);
            e.printStackTrace();
            callbackContext.error(errMsg);
        }
        return false;
    }


    /**
	 * Encode and print QR code
	 * 
	 * @param str
	 *          String to be encoded in QR.
	 * @param errCorrection
	 *          The degree of error correction. (48 <= n <= 51)
	 *          48 = level L / 7% recovery capacity.
	 *          49 = level M / 15% recovery capacity.
	 *          50 = level Q / 25% recovery capacity.
	 *          51 = level H / 30% recovery capacity.
	 *          
	 *  @param moduleSize
	 *  		The size of the QR module (pixel) in dots.
	 *  		The QR code will not print if it is too big.
	 *  		Try setting this low and experiment in making it larger.
	 */
     boolean printQR(CallbackContext callbackContext, String str) throws IOException {
        try {
	
		int errCorrect=48;
		int moduleSize=3;
		int pl = 3;
		int ph = 0;

		  //size function 67
		mmOutputStream.write(0x1D);
		mmOutputStream.write(0x28);
		mmOutputStream.write(0x6B);
		mmOutputStream.write(pl);
		mmOutputStream.write(ph);
		mmOutputStream.write(49);
		mmOutputStream.write(67);
		mmOutputStream.write(moduleSize);//1<= n <= 16


		  //error correction function 69
		mmOutputStream.write(0x1D);
		mmOutputStream.write(0x28); 
		mmOutputStream.write(0x6B);
		mmOutputStream.write(pl);
		mmOutputStream.write(ph); //ph
		mmOutputStream.write(49); //cn
		mmOutputStream.write(69); //fn
		mmOutputStream.write(errCorrect); //48<= n <= 51


		//save data function 80
		mmOutputStream.write(0x1D);//GS
		mmOutputStream.write(0x28); //(
		mmOutputStream.write(0x6B); //k
		//adjust height of barcode
		mmOutputStream.write(str.length()+3); //pl
		mmOutputStream.write(ph); //ph
		mmOutputStream.write(49); //cn
		mmOutputStream.write(80); //fn
		mmOutputStream.write(48); //
		mmOutputStream.write(str.getBytes());

		  
		  //print function 81
		mmOutputStream.write(0x1D);
		mmOutputStream.write(0x28);
		mmOutputStream.write(0x6B);
		mmOutputStream.write(pl); //pl
		mmOutputStream.write(ph); //ph
		mmOutputStream.write(49); //cn
		mmOutputStream.write(81); //fn
		mmOutputStream.write(48); //m  


			Log.d(LOG_TAG, "Data Sent");
            callbackContext.success("QR Sent");
            return true;

            }catch (Exception e) {
            	String errMsg = e.getMessage();
            	Log.e(LOG_TAG, errMsg);
            	e.printStackTrace();
            	callbackContext.error(errMsg);
        }
            	return false;
            
	}
	

	boolean printQRM(CallbackContext callbackContext, String qrdata) throws IOException {

        try {

    int store_len = qrdata.length() + 3;
    byte store_pL = (byte) (store_len % 256);
    byte store_pH = (byte) (store_len / 256);


    // QR Code: Select the model
    //              Hex     1D      28      6B      04      00      31      41      n1(x32)     n2(x00) - size of model
    // set n1 [49 x31, model 1] [50 x32, model 2] [51 x33, micro qr code]
    // https://reference.epson-biz.com/modules/ref_escpos/index.php?content_id=140
    byte[] modelQR = {(byte)0x1d, (byte)0x28, (byte)0x6b, (byte)0x04, (byte)0x00, (byte)0x31, (byte)0x41, (byte)0x31, (byte)0x00};

    // QR Code: Set the size of module
    // Hex      1D      28      6B      03      00      31      43      n
    // n depends on the printer
    // https://reference.epson-biz.com/modules/ref_escpos/index.php?content_id=141
    byte[] sizeQR = {(byte)0x1d, (byte)0x28, (byte)0x6b, (byte)0x03, (byte)0x00, (byte)0x31, (byte)0x43, (byte)0x03};


    //          Hex     1D      28      6B      03      00      31      45      n
    // Set n for error correction [48 x30 -> 7%] [49 x31-> 15%] [50 x32 -> 25%] [51 x33 -> 30%]
    // https://reference.epson-biz.com/modules/ref_escpos/index.php?content_id=142
    byte[] errorQR = {(byte)0x1d, (byte)0x28, (byte)0x6b, (byte)0x03, (byte)0x00, (byte)0x31, (byte)0x45, (byte)0x31};


    // QR Code: Store the data in the symbol storage area
    // Hex      1D      28      6B      pL      pH      31      50      30      d1...dk
    // https://reference.epson-biz.com/modules/ref_escpos/index.php?content_id=143
    //                        1D          28          6B         pL          pH  cn(49->x31) fn(80->x50) m(48->x30) d1…dk
    byte[] storeQR = {(byte)0x1d, (byte)0x28, (byte)0x6b, store_pL, store_pH, (byte)0x31, (byte)0x50, (byte)0x30};


    // QR Code: Print the symbol data in the symbol storage area
    // Hex      1D      28      6B      03      00      31      51      m
    // https://reference.epson-biz.com/modules/ref_escpos/index.php?content_id=144
    byte[] printQR = {(byte)0x1d, (byte)0x28, (byte)0x6b, (byte)0x03, (byte)0x00, (byte)0x31, (byte)0x51, (byte)0x30};

    // flush() runs the print job and clears out the print buffer
    //flush();

    // write() simply appends the data to the buffer
    mmOutputStream.write(modelQR);

    mmOutputStream.write(sizeQR);
    mmOutputStream.write(errorQR);
    mmOutputStream.write(storeQR);
    mmOutputStream.write(qrdata.getBytes());
    mmOutputStream.write(printQR);
    //flush();
    Log.d(LOG_TAG, "Data Sent QRM");
            callbackContext.success("QRM Sent");
            return true;

            }catch (Exception e) {
            	String errMsg = e.getMessage();
            	Log.e(LOG_TAG, errMsg);
            	e.printStackTrace();
            	callbackContext.error("QRM:"+errMsg);
        }
            	return false;
}


     boolean printQrcode(CallbackContext callbackContext, String qrcode) throws IOException {
        try {
        	byte[] contents = qrcode.getBytes();
        	//byte[] formats  = {(byte) 0x1d, (byte) 0x6b, (byte) 0x49};
        	//byte[] formats  = {(byte) 0x1d, (byte) 0x6b, (byte) 0x49, (byte) qrcode.length()};
        	//                    GS   (      k    pL   pH  cn fn      
        	byte[] formats  = { 0x1D, 0x28, 0x6B, 0x49,                (byte) 0x49, (byte) qrcode.length()};
		
			byte[] bytes    = new byte[formats.length + contents.length ];
			System.arraycopy(formats, 0, bytes, 0, formats.length );
			System.arraycopy(contents, 0, bytes, formats.length, contents.length);

			// add a terminating NULL
			//bytes[formats.length + contents.length] = (byte) 0x00;

			
            mmOutputStream.write(bytes);
            // tell the user data were sent
			//Log.d(LOG_TAG, "Qr Sent");
            callbackContext.success("Qr Sent:"+qrcode);
            return true;
        } catch (Exception e) {
            String errMsg = e.getMessage();
           // Log.e(LOG_TAG, errMsg);
            e.printStackTrace();
            callbackContext.error("Qr ERROR"+errMsg);
        }
        return false;
    }



	// disconnect bluetooth printer.
	boolean disconnectBT(CallbackContext callbackContext) throws IOException {
		try {
			stopWorker = true;
			mmOutputStream.close();
			mmInputStream.close();
			mmSocket.close();
			callbackContext.success("Bluetooth Disconnect");
			return true;
		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}




	public byte[] getText(String textStr) {
        // TODO Auto-generated method stubbyte[] send;
        byte[] send=null;
        try {
            send = textStr.getBytes("GBK");
        } catch (UnsupportedEncodingException e) {
            send = textStr.getBytes();
        }
        return send;
    }

    public static byte[] hexStringToBytes(String hexString) {
        hexString = hexString.toLowerCase();
        String[] hexStrings = hexString.split(" ");
        byte[] bytes = new byte[hexStrings.length];
        for (int i = 0; i < hexStrings.length; i++) {
            char[] hexChars = hexStrings[i].toCharArray();
            bytes[i] = (byte) (charToByte(hexChars[0]) << 4 | charToByte(hexChars[1]));
        }
        return bytes;
    }

    private static byte charToByte(char c) {
		return (byte) "0123456789abcdef".indexOf(c);
	}


   



	public byte[] getImage(Bitmap bitmap) {
        // TODO Auto-generated method stub
        int mWidth = bitmap.getWidth();
        int mHeight = bitmap.getHeight();
        bitmap=resizeImage(bitmap, 48 * 8, mHeight);
        //bitmap=resizeImage(bitmap, imageWidth * 8, mHeight);
        /*
        mWidth = bitmap.getWidth();
        mHeight = bitmap.getHeight();
        int[] mIntArray = new int[mWidth * mHeight];
        bitmap.getPixels(mIntArray, 0, mWidth, 0, 0, mWidth, mHeight);
        byte[]  bt =getBitmapData(mIntArray, mWidth, mHeight);*/

        byte[]  bt =getBitmapData(bitmap);


        /*try {//?????????????????
            createFile("/sdcard/demo.txt",bt);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }*/


        ////byte[]  bt =StartBmpToPrintCode(bitmap);

        bitmap.recycle();
        return bt;
    }


    private static Bitmap resizeImage(Bitmap bitmap, int w, int h) {
        Bitmap BitmapOrg = bitmap;
        int width = BitmapOrg.getWidth();
        int height = BitmapOrg.getHeight();

        if(width>w)
        {
            float scaleWidth = ((float) w) / width;
            float scaleHeight = ((float) h) / height+24;
            Matrix matrix = new Matrix();
            matrix.postScale(scaleWidth, scaleWidth);
            Bitmap resizedBitmap = Bitmap.createBitmap(BitmapOrg, 0, 0, width,
                    height, matrix, true);
            return resizedBitmap;
        }else{
            Bitmap resizedBitmap = Bitmap.createBitmap(w, height+24, Config.RGB_565);
            Canvas canvas = new Canvas(resizedBitmap);
            Paint paint = new Paint();
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(bitmap, (w-width)/2, 0, paint);
            return resizedBitmap;
        }
    }

    public static byte[] getBitmapData(Bitmap bitmap) {
		byte temp = 0;
		int j = 7;
		int start = 0;
		if (bitmap != null) {
			int mWidth = bitmap.getWidth();
			int mHeight = bitmap.getHeight();

			int[] mIntArray = new int[mWidth * mHeight];
			bitmap.getPixels(mIntArray, 0, mWidth, 0, 0, mWidth, mHeight);
			bitmap.recycle();
			byte []data=encodeYUV420SP(mIntArray, mWidth, mHeight);
			byte[] result = new byte[mWidth * mHeight / 8];
			for (int i = 0; i < mWidth * mHeight; i++) {
				temp = (byte) ((byte) (data[i] << j) + temp);
				j--;
				if (j < 0) {
					j = 7;
				}
				if (i % 8 == 7) {
					result[start++] = temp;
					temp = 0;
				}
			}
			if (j != 7) {
				result[start++] = temp;
			}

			int aHeight = 24 - mHeight % 24;
			int perline = mWidth / 8;
			byte[] add = new byte[aHeight * perline];
			byte[] nresult = new byte[mWidth * mHeight / 8 + aHeight * perline];
			System.arraycopy(result, 0, nresult, 0, result.length);
			System.arraycopy(add, 0, nresult, result.length, add.length);

			byte[] byteContent = new byte[(mWidth / 8 + 4)
					* (mHeight + aHeight)];// 
			byte[] bytehead = new byte[4];// 
			bytehead[0] = (byte) 0x1f;
			bytehead[1] = (byte) 0x10;
			bytehead[2] = (byte) (mWidth / 8);
			bytehead[3] = (byte) 0x00;
			for (int index = 0; index < mHeight + aHeight; index++) {
				System.arraycopy(bytehead, 0, byteContent, index
						* (perline + 4), 4);
				System.arraycopy(nresult, index * perline, byteContent, index
						* (perline + 4) + 4, perline);
			}
			return byteContent;
		}
		return null;

	}

	public static byte[] encodeYUV420SP(int[] rgba, int width, int height) {
		final int frameSize = width * height;
		byte[] yuv420sp=new byte[frameSize];
		int[] U, V;
		U = new int[frameSize];
		V = new int[frameSize];
		final int uvwidth = width / 2;
		int r, g, b, y, u, v;
		int bits = 8;
		int index = 0;
		int f = 0;
		for (int j = 0; j < height; j++) {
			for (int i = 0; i < width; i++) {
				r = (rgba[index] & 0xff000000) >> 24;
				g = (rgba[index] & 0xff0000) >> 16;
				b = (rgba[index] & 0xff00) >> 8;
				// rgb to yuv
				y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
				u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
				v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
				// clip y
				// yuv420sp[index++] = (byte) ((y < 0) ? 0 : ((y > 255) ? 255 :
				// y));
				byte temp = (byte) ((y < 0) ? 0 : ((y > 255) ? 255 : y));
				yuv420sp[index++] = temp > 0 ? (byte) 1 : (byte) 0;

				// {
				// if (f == 0) {
				// yuv420sp[index++] = 0;
				// f = 1;
				// } else {
				// yuv420sp[index++] = 1;
				// f = 0;
				// }

				// }

			}

		}
		f = 0;
		return yuv420sp;
	}







}

