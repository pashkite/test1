package dev.ashkite.limbusauto;

import android.util.Base64;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;

/** Grayscale control crops derived from the screenshot supplied for this prototype. */
final class VisionTemplates {
    private VisionTemplates(){}
    private static byte[] inflate(String s,int expected) {
        try {
            byte[] zipped=Base64.decode(s,Base64.DEFAULT);
            try(GZIPInputStream stream=new GZIPInputStream(new ByteArrayInputStream(zipped));
                ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buf=new byte[1024];int n;
                while((n=stream.read(buf))!=-1){out.write(buf,0,n);if(out.size()>4000)throw new IllegalStateException("Bad image data");}
                byte[] result=out.toByteArray();
                if(result.length!=expected)throw new IllegalStateException("Unexpected template size");
                return result;
            }
        }catch(Exception e){return new byte[0];}
    }
    private static final String GEAR_GZIP="H4sIAAAAAAAC/x2S61MaBxTFh32xC8uCIMER2piQqRQfISAVEYi8RFCRREEERBFWRQWEoFhUDDiAaABBk7TWxERjhCadiZPO5N/r2vPh3g/n3PPldwUCgUjYLmS6v/+4/fEtHyE3v/3dqD4l+Nx7HD6XxxdIJO2y/O33f5tfrt+fnb3/p3njYvEJLodL4Ex1rhg7/Hz99aq4t5MtFqu75xdelEngHBjFB0vNTx9vm8V5bWfHz2LpU69+Mndkv89ituCgaK/ZaFyeLYgFD2QjdrNM0m9P6sOVRbsORwB+pHHdqFn4Ek/hQ/Pm8iQzNjLdo1x8Xd9ugwC69OXlsZI7UPp8FXNs1Oqn6RlNT5divXagYcAI1r1lZJtPbl7Hdb0mm3OztLfYZ3ON5YvLOIDCLQ9aB4tXRZ/R7px+rtME87nAqEu+9nJVQ4dRHkZEPp26tcF8esrsm1IHd1KzBq1360UagmCY0V0/39SGavWdCflcbEITPcqmIrH4ugWE6Qik/3jstKX+vDhYTb4qx+W6nX1910w08AiEERiUZ7Ij7pPLN5vL1crFvrI7/krDH1/2/Eq1whAiGpapw4GlaLRUO88NGndLBp6FTIagO+GMts4hX496pVA5f5uQj2Up0xpKxP83WcSAQ+75rVXkOnx7YHscOsrJ2p6RiQgMAgAI6vJFq8FnkyhW0j7FaOYo0SFyLkRnaXeCFv6qJ42qMY/LYX5ijB1UJok+/7xVTANoAACryu/OUhNalbLr8XiyXHrRyXfMzrejCIphGMqxnnyol3fXnaZAJpffVRH9HnKIIHCCTbAIOtdeePdHtVzMlw+P1tScXmfQP8xDWGwOwbxvetiqWDt+Uy9XC7sucavSQ67NOfqHMYLg8hy5JdW9lt4Jv9dl+oXTrg6QqxG/zO7FMQYuXCrs7bi72SgdZSBC1WQwsZ4Ma/lSPcZkMtjuSr2Q2ghPmg3W6eBKdJlcIp9JlRYPxICZ0EPy+KS2nyDDG9FwNBmfeR7wzgbHDKMgQkNA5KepbPW0sh3f2k7nfp8yRbKZTNgzLWcjEEUNIzrG5/sUeutcOhk16RbnVmOWJ74FO04Ro94BgFh0agmHbEPSNp7I6J/WDwfJARSjblGQjoEgBEDMRx08BgKzO60hc58xKAYxqpoCR+GhJh0GIZAGwRy5ecQ9M8NFUQhiUgEAoiiAdxGABtIhkeE/8+WLVqADAAA=";
    private static final String WIN_GZIP="H4sIAAAAAAAC/yXR7WvTQADHcWaTJrlccrnLXe6ShubSJG3nQLG4RzvGNpVVZ8E9GAWHY4OirywOYaKyF/O5OFtL2Qth+EZh4B9pp5/XX35vfgRj7EBkQIhMExiaqhQu/TcxUVB1YEJoV64g27KQg5EFx8mYqowBrQAsaNpru5NZlsi4UpEySyvxBRlFVWlrhq7Ru7lHKZO+oCLKkih0sUuIg0mSGhAky5u3MHenDm6HzvRet9vdjynDLqaYVg1DbbTyNRjlw1+j7mJr0B8NT+qC/hvw6hC7S48eNN3k6Hjj6cmT1ll/+P5oyiE+q3JE64u7d/YfbzbJtTe9zx/7z3ZOf/44/zoXEO9q3nDcaudb52G+tYrC+68/fOo2W6Oz778Hk74Xbj9vRyy9PJ1ez9szSB7/ORye7y0Nel9OezUaJCurN2o804DqLW8sEHHvxeHbTmPm3auXBzsxF4K5LOA1pCnWbHsBIRLHWcCEH6eyTBij2EaYJfOwqPKb624g/bJfkmU/CHkpDIUQflCS8YquaubcuvA87oZhKQoYY5wxn3NfMEbmDUXR0llbhwCY1sUfRVDUAdR1oGtq8S/Te1CL0QEAAA=";
    static final byte[] GEAR=inflate(GEAR_GZIP,29*32);
    static final byte[] WIN=inflate(WIN_GZIP,31*15);
}
