package gov.usgs.cida.nar.service;

public enum DownloadServiceParameters {
	format("Format"),
	dataType("Data type"),
	qwDataType("Water Quality Data type"),
	streamFlowType("Stream Flow Time Series"),
	constituent("Constituent"),
	siteType("Site Type"),
	stationId("Station ID"),
	state("State"),
	startDateTime("Start Date/Time"),
	endDateTime("End Date/Time")
	;
	
	private String title;
	DownloadServiceParameters(String title) {
		this.title = title;
	}
	
	public String getTitle() {
		return title;
	}
}