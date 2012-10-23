import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.StringTokenizer;
import java.util.Vector;

import utils.FileOperation;

public class ScriptMaker {

	private static boolean first = true;
	private static final String HOME_DIR = "/ifi/asgard/a02/navneet/aggre/";
	private static final String SCRIPT_DIR = HOME_DIR + "scripts/";
	private static final String DEFAULT_CONFIG_FILE = "scriptmaker.properties";
	private static final String PORT = "1099";
	private static final int MEM_MIN = 256;
	private static final int MEM_MAX = 512;
	private static final String CONN = "rmi";
	private static int DELAY;
	private static Properties configProps;
	private static List<String> symbolList;
	private static int symbolSize;
	private static int symbolIndex;
	private static String outputDir;
	private static int counter = 1;
 
	
	private static Properties loadConfig(String configFile) {
		// Load file into a properties object first
		Properties configProps = new Properties();
		try {
			InputStream confFile = new FileInputStream(configFile);
			configProps.load(confFile);
			confFile.close();
		} catch (FileNotFoundException e1) {
			System.err.println("Failed: Configuration file '" 
				+ configFile + "' not found.");
			configProps = null;
		} catch (Exception e) {
			System.err.println("ERROR: Cannot load configuration file: " 
				+ configFile +  ":\n" + e);
			configProps = null;
		}
		
		return configProps;
	}
	
	private static void generateScript(String hostFile , String NP, String  NS, String  OP) {

		if (!FileOperation.exists(hostFile)) {
			System.out.println("host address file " + hostFile + " not found.");
			return;
		}
		
		String hostContents = FileOperation.getLastNLines(hostFile, Integer.MAX_VALUE);
		StringTokenizer hosts = new StringTokenizer(hostContents);
		List<Host> hostList = new Vector<Host>();
		
		while(hosts.hasMoreTokens()){
			StringTokenizer line = new StringTokenizer(hosts.nextToken(),";");
			hostList.add(new Host(line.nextToken(),line.nextToken(),counter));
			counter++;
		}
				
		int numCore = Integer.parseInt(configProps.getProperty("brokers.core"));
		int fanout = Integer.parseInt(configProps.getProperty("brokers.fanout"));
		
		if(hostList.size() < numCore + numCore*fanout){
			System.err.print("ERROR: Spawn more nodes.");
			System.exit(1);
		}

		DELAY = 30; /** wait for all the broker to be up  30 broker 90 sec**/		
//		DELAY = numCore*fanout*90; /** wait full **/		
//		DELAY = 0; /** wait nothing **/

		int pubPerNode = Integer.parseInt(configProps.getProperty("stock.publisher"));
		int subPerNode = Integer.parseInt(configProps.getProperty("stock.subscriber"));

		initSymbols();
		
		outputDir = configProps.getProperty("output.dir");
		
		int i = 0;
		int j = 0;
		
		Queue<Host> coreBrokers = new LinkedList<Host>();
		Host previousHost = null;
		String hostOrder = "";
		String coreOrder = "";
		
		for(Host host : hostList){
			if(i == 0){
				outputCore(host);
			} else {
				outputCore(host, previousHost);
			}
			
			coreBrokers.add(host);
			previousHost = host;
			coreOrder += host.getHostName() + "\n";
			i++;

			if(i == numCore)
				break;
		}
		
		hostList.removeAll(coreBrokers);

		i = 0;
		Host coreHost = null;
		for(Host host : hostList){
			if(i == 0){
				if(coreBrokers.isEmpty())
					break;
				
				 coreHost = coreBrokers.remove();
				first = true;
			}
			
			outputNormal(host, coreHost, pubPerNode, subPerNode, true , (((j++) % 4) + 1), NP, NS, OP);
			//outputNormal(host, coreHost, pubPerNode, subPerNode, true ,  1);
			hostOrder += host.getHostName() + "\n";
			i = (i + 1) % fanout;
			first = false;
		}
		
		outputOrder(coreOrder, hostOrder);
	}
	
	private static void outputOrder(String coreOrder, String hostOrder) {
		try {
			FileWriter fstream = new FileWriter(SCRIPT_DIR + "core.txt");
			BufferedWriter out = new BufferedWriter(fstream);
			
			out.write(coreOrder);
			out.close();
			fstream.close();
			
			fstream = new FileWriter(SCRIPT_DIR + "hosts.txt");
			out = new BufferedWriter(fstream);
			
			out.write(hostOrder);
			out.close();
			fstream.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void outputNormal(Host host, Host coreHost, int pubPerNode, 
		int subPerNode, boolean publisher, int number, String NP, String  NS, String  OP) {
		try {
			FileWriter fstream = new FileWriter(SCRIPT_DIR + host.getHostName());
			BufferedWriter out = new BufferedWriter(fstream);
			
			writeHeader(out);
			
			String symbol = distributeSymbol();
			
			String line = HOME_DIR + "padres/bin/startbroker -Xms " + MEM_MIN + " -Xmx " + MEM_MAX + " -uri " + CONN + "://" 
				+ host.getIpAddr() + ":" + PORT
				+ "/Broker" + host.getId()
				+ " -n " + CONN + "://" + coreHost.getIpAddr() + ":" + PORT + "/Broker" + coreHost.getId()
				+ " > " + outputDir + "Broker" + host.getId() + ".log &\n";
			
			out.write(line);
			out.write("sleep $1\n");
			
			if(publisher){
				
				String localNP = "50";
				if(NP != null)
					localNP = NP;
				
				line = HOME_DIR + "padres/demo/bin/stockquote/startSQpublisher.sh -i"
					+ " Pub" + host.getId()
					+ " -NP " + localNP
					+ " -s " + symbol
					+ " -r 60 -d " + DELAY
					+ " -b " + CONN + "://"
					+ host.getIpAddr() + ":" + PORT
					+ "/Broker" + host.getId();
//					+ " > " + outputDir + "Pub" + host.getHostName() + ".log &\n";			
			
				out.write(line);
			out.write(" &\n sleep $1\n");
			}
			
			String localNS = "50";
			if(NS != null)
				localNS = NS;
			
			String localOP = "count";
			if(OP != null)
				localOP = OP;
			
//			if(!first){
			line = HOME_DIR + "padres/demo/bin/stockquote/startSQsubscriber.sh -i"
				+ " Client" + host.getId()
				+ " -NS " + localNS
				+ " -s \"[class,eq,'STOCK'],[symbol,eq,'" + symbol + "'],[AGR,eq,'"+localOP+"'],[PAR,eq,volume],[PRD,eq,'" + number + "'],[NTF,eq,'1']\""
				+ " -b " + CONN + "://"
				+ host.getIpAddr() + ":" + PORT
				+ "/Broker" + host.getId()
				+ " > " + outputDir + "Sub" + host.getId() + ".log &\n";
			
			out.write(line);
			
			/*if(number%4 == 1) {
				line = HOME_DIR + "padres/demo/bin/stockquote/startSQsubscriber.sh -i"
						+ " Client" + host.getId()
						+ " -NS " + localNS
						+ " -s \"[class,eq,'STOCK'],[symbol,eq,'" + symbol + "']\""
						+ " -b " + CONN + "://"
						+ host.getIpAddr() + ":" + PORT
						+ "/Broker" + host.getId()
						+ " > " + outputDir + "Sub" + host.getId() + ".log &\n";
			}
			out.write(line); */
//			}			

			out.close();
			fstream.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static String distributeSymbol() {
		String symbol = symbolList.get(symbolIndex);
		symbolIndex = (symbolIndex + 1) % symbolSize;
		return symbol;
	}

	private static void outputCore(Host host) {
		outputCore(host, null);
	}

	private static void outputCore(Host host, Host previousHost) {
		try {
			FileWriter fstream = new FileWriter(SCRIPT_DIR + host.getHostName());
			BufferedWriter out = new BufferedWriter(fstream);
			
			writeHeader(out);
			
			String line = HOME_DIR + "padres/bin/startbroker -Xms " + MEM_MIN + " -Xmx " + MEM_MAX + " -uri " + CONN + "://" 
				+ host.getIpAddr() + ":" + PORT
				+ "/Broker" + host.getId() ;
			
			if(previousHost != null)
				line += " -n " + CONN + "://" + previousHost.getIpAddr() + ":" + PORT + "/Broker" + previousHost.getId();
			
			line += " > " + outputDir + "Broker" + host.getId() + ".log &";
			
			out.write(line);
			
			out.close();
			fstream.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void writeHeader(BufferedWriter out) throws IOException {
		//out.write("mkdir /dev/shm/kzhang/\nmkdir /dev/shm/kzhang/logs\nmkdir /dev/shm/kzhang/data\n");
	}


	private static void initSymbols() {
		StringTokenizer symbols = new StringTokenizer(configProps.getProperty("stock.symbols"),";");
		symbolList = new Vector<String>();
		
		while(symbols.hasMoreTokens()){
			symbolList.add(symbols.nextToken());
		}
		
		symbolIndex = 0;
		symbolSize = symbolList.size();
		
		if(symbolSize == 0){
			System.err.print("ERROR: Need a symbol");
			System.exit(1);
		}
	}

	public static void main(String[] args) {
		if(args.length < 1){
			System.err.println("java ScriptMaker <hostfile>");
			System.exit(1);
		}
		
		configProps = loadConfig(DEFAULT_CONFIG_FILE);
		
		if(configProps != null) {
			
			
			final List<String> list =  new ArrayList<String>();
			//defalut value of replicaiton
			String NP = null;
			String NS = null;
			String OP = null;
			Collections.addAll(list, args); 
			Iterator itr = list.iterator();

			while(itr.hasNext()) {
				String curr = (String) itr.next();
				if( curr.equals("-NP")){
					NP = (String) itr.next();
				}
				
				if( curr.equals("-NS")){
					NS = (String) itr.next();
				}
				
				if( curr.equals("-OP")){
					OP =  (String) itr.next();
				}
			}
			
			generateScript(args[0], NP, NS, OP);
			
		}
		else {
			
			System.exit(1);
		}
	}
}
