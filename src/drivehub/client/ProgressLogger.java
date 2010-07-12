package drivehub.client;

public interface ProgressLogger {

	void info(String state);

	void info(String state, String details);
	
	void error(String state, Exception e);
	
}
