import java.io.IOException;
import java.security.GeneralSecurityException;

public abstract class Configurator {

    static String login;

    static {
        try {
            login = Decrypter.decrypt("");
        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
        }
    }

    static char[] password;

    static {
        try {
            password = Decrypter.decrypt("").toCharArray();
        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
        }
    }

    static int port = 9990;

    protected Configurator() {
    }
}
