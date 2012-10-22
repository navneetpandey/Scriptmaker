
public class Host {

	private String hostName;
	private String ipAddr;
	private int id;
	
	public Host(String hostName, String ipAddr, int id){
		this.hostName = hostName;
		this.ipAddr = ipAddr;
		this.id = id;
	}

	public String getHostName() {
		return hostName;
	}

	public String getIpAddr() {
		return ipAddr;
	}

	@Override
	public String toString(){
		return "(" + hostName + "," + ipAddr + ")"; 
	}

	public Host duplicate() {
		return new Host(hostName, ipAddr, id);
	}

	public int getId() {
		return id;
	}
	
}
