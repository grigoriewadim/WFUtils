import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

public class Main {

    private static ArrayList<String> hosts = new ArrayList<>();
    private static ArrayList<String> applications = new ArrayList<>();
    private static Commands command;

    private static void setArgument(String[] args) {
        String[] argumentsCMD = Arrays.asList(args).iterator().next().split(";");
        try {
            for (String arguments : argumentsCMD) {
                if (arguments.contains("host")) {
                    arguments = arguments.substring(arguments.lastIndexOf(":") + 1);
                    StringTokenizer st = new StringTokenizer(arguments, ",");
                    int count = st.countTokens();
                    for (int i = 0; i < count; i++) {
                        hosts.add(st.nextToken());
                    }
                }
                if (arguments.contains("apps")) {
                    arguments = arguments.substring(arguments.lastIndexOf(":") + 1);
                    StringTokenizer st = new StringTokenizer(arguments, ",");
                    int count = st.countTokens();
                    for (int i = 0; i < count; i++) {
                        applications.add(st.nextToken());
                    }
                }
                if (arguments.contains("command")) {
                    arguments = arguments.substring(arguments.lastIndexOf(":") + 1);
                    if (arguments.contains("start")) {
                        command = Commands.START;
                    }
                    if (arguments.contains("stop")) {
                        command = Commands.STOP;
                    }
                    if (arguments.contains("restart")) {
                        command = Commands.RESTART;
                    }
                }
            }
        } catch (NullPointerException e) {
            System.out.println(new Exception("Недостаточно параметров для запуска") + "\n" + e.fillInStackTrace());
        }
    }

    private static void falseException() {
        System.out.println("Командлету не удалось распознать команду, обратитесь пожалуйста к администратору за помощью");
    }

    private static Boolean checkStatus(String nameApplication, String host) {
        Map<String, String> appStatus = new HashMap<>();
        ModelControllerClient cli;
        try {
            cli = ModelControllerClient.Factory.create(InetAddress.getByName(host), Configurator.port,
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
            final ModelNode names = Operations.createAddOperation(ModelNode.fromString("read-children-resources"));
            names.get(ClientConstants.CHILD_TYPE).set(ClientConstants.DEPLOYMENT);
            final ModelNode nameResult = cli.execute(names);
            final ModelNode deployments = Operations.readResult(nameResult);
            for (String name : deployments.keys()) {
                final ModelNode nameList = deployments.get(name);
                appStatus.put(Arrays.toString(nameList.get("runtim-name").toString().split(",")),
                        Arrays.toString(nameList.get("enabled").toString().split(",")) + "\n");
            }
            /*Проверяем выключено ли приложение для рестарта*/
            for (Map.Entry<String, String> entry : appStatus.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key.contains(nameApplication)) {
                    if (value.contains("true")) {
                        cli.close();
                        return true;
                    }
                }
            }
            cli.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static void restart(String host, String nameDeployment) {
        String deploy = "/deployment=" + nameDeployment + " /:deploy";
        String undeploy = "/deployment=" + nameDeployment + " /:undeploy";
        if (checkStatus(nameDeployment, host)) {
            while (checkStatus(nameDeployment, host)) {
                try {
                    new Client(host, Configurator.port, undeploy);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                new Client(host, Configurator.port, deploy);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void runCommand() {
        String runtimeName;
        String request;
        try {
            for (Object host : hosts) {
                for (Object application : applications) {
                    runtimeName = (String) application;
                    if (command.equals(Commands.START)) {
                        request = "/deployment=" + runtimeName + " /:deploy";
                        new Client((String) host, Configurator.port, request);
                        report(host, runtimeName, "enabled");
                    }
                    if (command.equals(Commands.STOP)) {
                        request = "/deployment=" + runtimeName + " /:undeploy";
                        new Client((String) host, Configurator.port, request);
                        report(host, runtimeName, "disabled");
                    }
                    if (command.equals(Commands.RESTART)) {
                        restart((String) host, runtimeName);
                        report(host, runtimeName, "enabled");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void report(Object server, String deployment, String status) {
        Map<String, String>  reportCollection = new HashMap<>();
        reportCollection.put((String) server, deployment + " -> " + status);
        File report = new File("reportservice.txt");
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(report, true));
            for (Map.Entry<String, String> entry : reportCollection.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                String outputString = "Server: " + key + "; Deployment: " + value + ";" + "\n";
                writer.append(outputString);
            }
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            setArgument(args);
            runCommand();
        } catch (NoSuchElementException e) {
            System.out.println("Недостаточно элементов в командной строке");
        }
    }

}
