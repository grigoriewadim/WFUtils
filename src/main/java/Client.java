import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.controller.client.ModelControllerClient;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import java.io.IOException;
import java.net.InetAddress;

public class Client {

    private static ModelControllerClient client;
    private static CommandContext ctx;

    Client(String host, int port, String request) throws IOException {
        try {
            client = ModelControllerClient.Factory.create(
                    InetAddress.getByName(host), Configurator.port,
                    callbacks -> {
                        for (Callback current : callbacks) {
                            if (current instanceof NameCallback) {
                                NameCallback ncb = (NameCallback) current;
                                ncb.setName(Configurator.login);
                            } else if (current instanceof PasswordCallback) {
                                PasswordCallback pcb = (PasswordCallback) current;
                                pcb.setPassword(Configurator.password);
                            } else if (current instanceof RealmCallback) {
                                RealmCallback rcb = (RealmCallback) current;
                                rcb.setText(rcb.getDefaultText());
                            } else {
                                throw new UnsupportedCallbackException(current);
                            }
                        }
                    });


        } catch (java.net.UnknownHostException | NullPointerException e) {
            e.printStackTrace();
        }

        try {
            ctx = org.jboss.as.cli.CommandContextFactory.getInstance().newCommandContext(host, Configurator.login, Configurator.password);
        } catch (CommandLineException e) {
            e.printStackTrace();
        }

        try {
            ctx.connectController();
            ctx.handle(request);
        } catch (CommandLineException e) {
            e.printStackTrace();
        }
        client.close();
    }
}
