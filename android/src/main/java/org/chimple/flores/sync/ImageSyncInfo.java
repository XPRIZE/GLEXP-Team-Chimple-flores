package org.chimple.flores.sync;

import android.content.Context;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ImageSyncInfo extends Thread {
    Socket socket=null;
    Context context;
    int count=0;

    ImageSyncInfo(Socket socket,Context context){
        this.socket=socket;
        this.context=context;
    }

    @Override
    public void run() {
        try {
            count=count+1;
            if (socket != null) {
                InputStream inputstream = socket.getInputStream();
                File folder = new File(context.getExternalFilesDir(null), "P2P_IMAGES");
                if (!folder.exists()) {
                    folder.mkdirs();
                }
                int len;
                File file = new File(folder, "Receivedimage.jpg");
                FileOutputStream out = new FileOutputStream(file);
                byte[] bytes = new byte[1024];
                BufferedOutputStream bos = new BufferedOutputStream(out);
                while ((len = inputstream.read(bytes)) != -1) {
                    bos.write(bytes, 0, len);
                }
                bos.close();
//                inputstream.close();
                Log.d("Reading Byte image", String.valueOf(bytes.length));
            }
            }catch(FileNotFoundException e){
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch(IOException e){
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }


    public boolean write(Socket socket){
        try {
            OutputStream stream = socket.getOutputStream();
            File file = new File(context.getApplicationContext().getExternalFilesDir(null) + "/Cache", "DefaultImage.jpg");
            InputStream inputStream = null;
            inputStream = new FileInputStream(file.getAbsolutePath());
            byte[] buf = new byte[1024];
            int len;
            try {
                while ((len = inputStream.read(buf)) != -1) {
                    stream.write(buf, 0, len);
                    Log.d("Writing Byte image", String.valueOf(buf.length) +":"+ buf.toString());
                }
//                stream.close();
                inputStream.close();
            } catch (IOException e) {
                Log.d("write error", e.toString());
                return false;
            }

        } catch (IOException e) {
            e.printStackTrace();
            Log.d("SendingImageSync", e.toString());
        }
        return true;
    }

}
