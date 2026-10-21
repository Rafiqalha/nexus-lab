package org.nexus.lab;
import java.nio.ByteBuffer;
import java.util.regex.*;
/** Host JVM smoke of the exact JNI exports; not Android/camera performance evidence. */
public final class Native {
    public static native long open();
    public static native long create(long session);
    public static native String submit(long capture, ByteBuffer pixels, int width, int height, int rotation, long timestamp);
    public static native String progress(long capture);
    public static native long finish(long capture);
    public static native String read(long capture,long result);
    public static native int cancel(long capture);
    public static native int release(long session,long capture,long result);
    private static void require(boolean value) { if(!value) throw new AssertionError(); }
    private static long number(String json,String key) {
        Matcher m=Pattern.compile("\""+key+"\":(\\d+)").matcher(json);require(m.find());return Long.parseLong(m.group(1));
    }
    public static void main(String[] args) throws Exception {
        System.load(args[0]);System.load(args[1]);
        for(int i=0;i<80;i++) {
            long s=open(),c=create(s);require(cancel(c)==0);require(number(progress(c),"state")==4);
            require(release(s,c,0)==0);require(release(s,c,0)!=0);
        }
        for(int rotation:new int[]{0,90,180,270}) for(int channel=0;channel<3;channel++) {
            long s=open(),c=create(s);ByteBuffer buffer=ByteBuffer.allocateDirect(48);
            try {
                for(int i=0;i<12;i++)for(int k=0;k<4;k++)buffer.put((byte)(k==channel?255:0));buffer.flip();
                String p=submit(c,buffer,3,4,rotation,1);require(number(p,"sdk_status")==0);require(number(p,"retry")==1);
                require(p.contains("\"quality\":[0.0,0.0,0.0,"+new int[]{77,149,29}[channel]+".0,"));
            } finally { while(buffer.hasRemaining())buffer.put((byte)0);require(release(s,c,0)==0); }
        }
        long s=open(),c=create(s),r=0;ByteBuffer buffer=ByteBuffer.allocateDirect(1024);
        try {
            for(ByteBuffer invalid:new ByteBuffer[]{ByteBuffer.allocate(1024),ByteBuffer.allocateDirect(1024).position(4),ByteBuffer.allocateDirect(1024).limit(1020)}) {
                try {submit(c,invalid,16,16,0,1);throw new AssertionError();}catch(IllegalStateException expected){}
            }
            for(int i=0;i<7;i++) {
                long until=System.nanoTime()+3_000_000_000L;
                while(number(progress(c),"elapsed_ms")<i*120) { require(System.nanoTime()<until);Thread.sleep(2); }
                buffer.clear();for(int n=0;n<256;n++){int v=(n*83+n/16*47+i)%256;buffer.put((byte)v).put((byte)v).put((byte)v).put((byte)255);}buffer.flip();
                if(number(submit(c,buffer,16,16,270,i+1),"state")==2)break;
            }
            r=finish(c);String result=read(c,r);require(result.contains("\"current_result_valid\":true"));require(number(result,"decision")==1);require(number(result,"product_eligible")==0);
        } finally { require(release(s,c,r)==0); }
        require(read(c,r).contains("\"current_result_valid\":false"));
        System.out.println("HOST_JVM_JNI PASS: color channels/orientations, real SDK-clock sampling, final validation, stale read, 80 cancel/release cycles; no camera/device performance claim.");
    }
}
