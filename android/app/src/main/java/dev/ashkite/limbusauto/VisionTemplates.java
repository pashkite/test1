package dev.ashkite.limbusauto;
import android.util.Base64;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
final class VisionTemplates {
    private VisionTemplates(){}
    private static byte[] unzip(String s,int expected){
        try(GZIPInputStream stream=new GZIPInputStream(
                new ByteArrayInputStream(Base64.decode(s,Base64.DEFAULT)));
            ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[512];int n;
            while((n=stream.read(buf))!=-1){
                out.write(buf,0,n);if(out.size()>4000)throw new IllegalStateException("Bad asset");
            }
            byte[] result=out.toByteArray();
            return result.length==expected?result:new byte[0];
        }catch(Exception e){return new byte[0];}
    }
    static final byte[] IDLE=unzip("H4sIAAAAAAAC/z2QzUsCURTFl41azXvvvo8Zx3AcRyVSBDNsUYsigqIWLdpkm0IFU9pk0LJatOgLrRyzok1J0Mcm7P/rzgSdu/px4Nxzr+M4CZSdSDjOinfDARijlBCdyM59vjCd4sgMsept2f2fA4ZICJG3HUNdtMvo+ip1K9x4fq5RAMqsbNXLqNjwe8CBUbFYOfUsaX4ONigDyDQPrz2ljNZwnwIzl3Z2L3sxEW00FhjwWDxu170cM+ZnXernUlq8qxqmwFpBLwrdWzWBsQFQQqveHjX/FXXaD8lQSEOFQuGwPla672t/GtFGI1pkveem3LTrK5lMTllzIjgY/IPHxwqgpOR+aUwmujTT6VSK84CIruTTS/2j5PdgVNel8divvWWxyUQefDw+f/0qCpBLNZfowjhpXy1j9mSztRYjUj0Vjt4NsBbK25s5JtRZ/26VccuO2wlHCiGA42AuPlpHlgLwV8Fiqsxgpe+hSWYivwKnRGOzAQAA",15*29);
    static final byte[] SELECTED=unzip("H4sIAAAAAAAC/wsLCQ0JBYOQkMhCASsrSyMQMDTQ19XwKwwJ9bcwMzM3MzEzMdTTaZu5arOuOZDvmuoN5Fd193drWFha2tfPn+psrF81f940XUsr27gFk2dXWBtUr9+8NsjS1nPylKn9iwJNanv31Khb2RfNmzJnWt802+r86Zu1rP1mT10wb0H//LjqnOwMDZvwGUtXrFy2fFF+jYOtrYqlU2hCUmJiQqxTta2NtaWlpZW1lZWFuYFqjYW5qakJEBgbG+nrxAXa2dvbAYE9kHJy8eBkhQI2IGJhZmaBA2YmZmYmVTBQAQFlVX1mMTFRERERIUEg4OeXNZSQkBATEREWFgKKCAiZaKrLaUqIAHkCggIC/MaTtzlvLQSqBsoLCPAZzVnnuC1ZDMgzkgRxp7ZN3FUiIiyikWUjJsBrnDWxt1RCVEg6pCRDByi73dV5m7OIqE5EXIydHJ9RxtRpBRKiUnIKCoqK8nxGEuKiwqJga4UE+PmMwa4SAnJBzuA2ExGBMMGO1FIAALxD0XqzAQAA",15*29);
}
