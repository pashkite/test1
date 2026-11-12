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
    private static final String GEAR_GZIP="H4sIADVe9WoC/w2Oa1cSCQBAP5gIMjMyCAN2Kt/bqqShIU9DWiJEEDUZIECQCXRw0EjY3BUnAsFhFBRREIp8orJau3us/X3r/XrPuefCXC7M5Yryl99vf5y+aGpkC/1fY4Y2Po/H49+BIDxYWv3x87+f/6ZNGvlU5e+bSvgZX9gibBYKEQTmGGu3t7f/fL8+/1yq3Hy7qZ04+l4ZlL0tIn3PQ12ydnN9/Vftqnq4R9GVi4tjlxYPzhhV5BEVLV3Wrk531xZmPYEPG6lIpPAVH/Ak8Yn4We2yWq0ehHTdAg4IcpAe80sF+mkfk/uXDdWrq4vzk6gSASDkl35RF8IZlMz5taG9d3bf9Nll9ayMtbP5cu+fuUJ++4P1ldLpHpQRO2lqJXd+cojygadLpfMvO6nS8elnfGx0sK9PurSbSVhzZTcPVG+fn+y+UetC0XRhL+BR9ImGtetbyc6ZwEPW6F61lMIUoqERtRFPJMNeiSaks6ZSvI5uZi99WVwzK8YsTvukUjxNpn53PLfJh8MbHEHXg+BZJWZQe0jSMWZ2GSTTsVjktaxf7IlBMDJ8cJq1yrFNKqnrlkzPjUvcG3Fy5S2+sAo1wehRMaKcjB2dlj0vJlfXgiq5i4oTuimMgJp4weOcQ4oliic5Yi2RL2/oemXRjJ7VOYdBEKwlqUnFytFxPuF4l9n/kp18PBDasbEEdicIgvCjUWmPmcDmfGgwnS/voCITueNmc62zwB1Qq6hDb+iUh2k6XTiM6wdmE9tOVjPqZAMgADxQDZhszcxeS/ygmFtUDBMUbWYIX7uAuy7fGNHInDpefbM1nV01PBn7Y2tDVtdtwUCoSWDJZRYUKjsq65DOExODqoUEHX5UJ7XiTRB4P3RYTvuVsnGLbcasEY/4SDo5xYZM9jsJAc+zlfIm/lI6LBOLhsYDZJoOddT1oR7H3SwbdhQrpQy5aJ8YHDJF4ptUdKQBNqJ+ORsEGxkCLF8q7WeTUT+xltpMRkYBlsritotAoBHk3BNYNgvFQn43u01T8UUpmyW2uQnzSCMLeGppZ4Bibyyzm81QH4NjbQy2wukiiFlVPeM+vr4oAeqhLpUJnfmtv4XJQDTY/PJywPG4ntG+TkXfm3tgxr0GFpNRz2xRoN5g+H3Q+wxsYLaR+dzH0JJD29ve2vqrXP/GNx8ILeF+NSJoZPFd9F5u61NkwTv7dt7t8xGhJcxmNuoVA7L/AZghtdGgAwAA";
    private static final String WIN_GZIP="H4sIADZe9WoC/y2Qx0sDQRSH76bs9E0mmy3JwRaNilgjiCWW6FoQFDE2EI2F3ATFggd1iRrFRrzYYg6K+Df6dvUb5vJ+7z1mvlB2xtSicDRd16J/aBFAAqYezmYEZYwJIVThoXLGKCWEYMxr6uwExoRUjyeEqib7B4BGDgkCFCXaPmIhHG7e/d5uNmX27vb2vrIhKPmPZceIhZldejl9KuWM7cpN8bqUFe5iuBD3QCym9jLJwc01c+v58eH+blmFaWLUC6TI1LCF5eZV4eLy8rj9pFx++ng/sCAO983VEUW2pU3EUyuHF2c7o7HF1zfno7II06xhNT+qI5kashAZKBXWj8o5fb5YcIpfOUFppHt23m7gsjNtIDL8er60/5mP5n+KjuNMc8qkGYvHLSFb+g2EQ60zqwu9eqhz0h4fyyRdJ/B2+LxsTMcUhCnjHLSAN84ocZ25IVY0Y6KLEQJa/3JoAMMg1dOq1iJ7KCy4W1Y95xzmmdcMhUjI15QI+gPBYADw+30uVVUBr4CCft8vx2/n+tEBAAA";
    static final byte[] GEAR=inflate(GEAR_GZIP,29*32);
    static final byte[] WIN=inflate(WIN_GZIP,31*15);
}
