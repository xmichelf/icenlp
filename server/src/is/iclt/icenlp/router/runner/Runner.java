package is.iclt.icenlp.router.runner;

import is.iclt.icenlp.common.configuration.Configuration;
import is.iclt.icenlp.router.threads.RequestListneningThread;
import is.iclt.icenlp.router.threads.RequestListneningThreadException;
import is.iclt.icenlp.router.threads.SlaveListeningThread;
import is.iclt.icenlp.router.threads.SlaveListeningThreadException;

import java.io.File;

/**
 * Runner class for the router.
 * @author Hlynur Sigurþórsson
 */
public class Runner {
	public static String prefix = "[RouterRunner]: ";

	public static void main(String[] args) {
		System.out.println("- IceNLP router");
        
        if(args.length >= 2){
            System.out.println(prefix + "error in parameters.");
            System.out.println(prefix + "stopping.");
            help();
            return;
        }

        else if(args.length == 1){

            if(args[0].equals("--help")){
                help();
                return;
            }

            String configFileLocation = args[0];
            File f = new File(configFileLocation);
            if(f.exists() && f.canRead()){
                System.out.println(prefix + "reading configuration file from: " + configFileLocation);
                Configuration.loadConfig(args[0]);
            }
            else{
                System.out.println(prefix + "unable to read configuration file from: " + configFileLocation);
                System.out.println(prefix + "stopping.");
                return;
            }
        }

        else{
            File f = new File("router.conf");
            System.out.println(prefix + "Reading default configuration file: " + f.getAbsolutePath());
            if(f.exists() && f.canRead())
                Configuration.loadConfig("router.conf");
            else{
                System.out.println(prefix + "unable to read default configuration file: " + f.getAbsolutePath());
                System.out.println(prefix + "stopping.");
                return;
            }
        }

        // The hostname for the router server.
		// default hostname is set to "localhost".
		String hostName = "localhost";

		// The port that the server listens to for
		// incoming slaves. Default port is 2525.
		String slavePort = Configuration.getInstance().getValue("slaveport");

		// The port that the server listens to for
		// incoming translation requests. Default port
		// is 2526.
		String requestPort = Configuration.getInstance().getValue("requestport");
        
		// Let's display all the settings.
		System.out.println(prefix + "hostname set to " + Configuration.getInstance().getValue("hostname"));

		// Let's start the slave listening thread.
		SlaveListeningThread slaveListeningThread = null;
		try {
			slaveListeningThread = new SlaveListeningThread(hostName, slavePort);
		} catch (SlaveListeningThreadException e) {
			System.out.println(prefix
					+ "unable to start Slave Listening thread: "
					+ e.getLocalizedMessage() + ". Shutting down.");
			return;
		}

		new Thread(slaveListeningThread).start();

		// Let's start the request listening thread.
		RequestListneningThread requestThread = null;
		try {
			requestThread = new RequestListneningThread(hostName, requestPort);
		} catch (RequestListneningThreadException e) {
			System.out.println(prefix
					+ "unable to start Request Listening thread: "
					+ e.getLocalizedMessage() + ". Shutting down.");
			return;
		}

		new Thread(requestThread).start();
	}

	public static void help() {
		System.out.println("parameters:");
		System.out.println("[configuration file]      Path to configuration file to load.");
		System.out.println("--help                    Prints this menu.");
	}
}