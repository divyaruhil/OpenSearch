package org.opensearch.bitmap;

import org.roaringbitmap.longlong.Roaring64NavigableMap;
import java.io.*;
import java.util.Base64;

public class GenBitmap64 {
    public static void main(String[] args) throws Exception {
        Roaring64NavigableMap bitmap = new Roaring64NavigableMap();
        bitmap.addLong(1000000000000L);
        bitmap.addLong(3000000000000L);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        bitmap.serializePortable(dos);
        dos.close();

        String base64 = Base64.getEncoder().encodeToString(bos.toByteArray());
        System.out.println(base64);
    }
}

