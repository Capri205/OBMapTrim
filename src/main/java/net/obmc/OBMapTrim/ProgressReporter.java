package net.obmc.OBMapTrim;

public class ProgressReporter {

	private int totalWork;
	private int doneWork;
	private int progressRate;
	private String prefix;

	public ProgressReporter( String prefix, int totalWork ) {
		
		this.totalWork = totalWork;
		this.doneWork = 0;
		this.progressRate = 0;
		this.prefix = prefix;
		
		System.out.print( prefix + ": 0% " );
	}
	
	public void updateSet( int sofar ) {
		doneWork = sofar;
		outputProgress();
	}
	
	public void updateStep( int increment ) {
		doneWork += increment;
		outputProgress();
	}

	
	public void updateOne() {
		++doneWork;
		outputProgress();
	}
	
	private void outputProgress() {
		int currentRate = doneWork * 100 / totalWork;
		if ( currentRate == progressRate ) {
			return;
		}
		progressRate = currentRate;
		OBMapTrim.outputMsg( String.format("\r%s: %d%% ", prefix, progressRate ), true, false, false );
		//System.out.printf( "\r%s: %d%% ", prefix, progressRate );
	}
	
	public void close() {
		//System.out.print( "\r" );
		OBMapTrim.outputMsg( String.format("\r"), false, false, false);
	}
}

