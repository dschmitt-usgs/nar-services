package gov.usgs.cida.nar.service;

import gov.usgs.cida.nar.connector.WFSConnector;
import gov.usgs.cida.nar.util.JNDISingleton;
import gov.usgs.cida.nude.column.Column;
import gov.usgs.cida.nude.column.ColumnGrouping;
import gov.usgs.cida.nude.filter.ColumnTransform;
import gov.usgs.cida.nude.filter.FilterStageBuilder;
import gov.usgs.cida.nude.filter.FilterStep;
import gov.usgs.cida.nude.filter.NudeFilterBuilder;
import gov.usgs.cida.nude.out.Dispatcher;
import gov.usgs.cida.nude.out.StreamResponse;
import gov.usgs.cida.nude.out.TableResponse;
import gov.usgs.cida.nude.plan.Plan;
import gov.usgs.cida.nude.plan.PlanStep;
import gov.usgs.cida.nude.resultset.inmemory.TableRow;
import gov.usgs.webservices.framework.basic.MimeType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.xml.stream.XMLStreamException;

public class SiteInformationService implements INarStreamService {
	public static final String SITE_ATTRIBUTE_TITLE = DownloadType.siteAttribute.getTitle();
	public static final String SITE_ATTRIBUTE_OUT_FILENAME = SITE_ATTRIBUTE_TITLE.replaceAll(" ", "_");
	
	private static final String SITE_INFO_URL_JNDI_NAME = "nar.endpoint.ows";
	private static final String SITE_LAYER_NAME = "NAR:JD_NFSN_sites0914";
	
	public void streamData(OutputStream output, final Map<String, String[]> params) throws IOException {
		Client client = ClientBuilder.newClient();
		
		InputStream returnStream = (InputStream)client.target(buildSiteInfoRequest(params))
                .path("")
                .request(new MediaType[] {MediaType.APPLICATION_OCTET_STREAM_TYPE})
                .get(InputStream.class);
		
		final WFSConnector wfsConnector = new WFSConnector(returnStream);
		List<PlanStep> steps = new LinkedList<>();
		PlanStep connectorStep = new PlanStep() {

			@Override
			public ResultSet runStep(ResultSet rs) {
				return wfsConnector.getResultSet();
			}

			@Override
			public ColumnGrouping getExpectedColumns() {
				return wfsConnector.getExpectedColumns();
			}
		};
		
		steps.add(connectorStep);
		
		FilterStageBuilder fsb = new FilterStageBuilder(connectorStep.getExpectedColumns());
		ColumnGrouping fromConnectorStep = connectorStep.getExpectedColumns();
		for (Column col : fromConnectorStep) {
			if (!fromConnectorStep.getPrimaryKey().equals(col)) {
//				final Column outCol = col;
//				fsb.addTransform(col, new ColumnTransform() {
//					@Override
//					public String transform(TableRow row) {
//						return "\"" + row.getValue(outCol) + ",\"";
//					}
//				});
			}
		}
		
		FilterStep wrapInQuotesFilterStep = new FilterStep(new NudeFilterBuilder(connectorStep.getExpectedColumns())
				.addFilterStage(fsb.buildFilterStage())
				.buildFilter());
		
		steps.add(wrapInQuotesFilterStep);
		List<Column> wrapped = wrapInQuotesFilterStep.getExpectedColumns().getColumns();
		ColumnGrouping removed = new ColumnGrouping(wrapped.subList(1, wrapped.size() - 1));
		FilterStep removeFIDFilterStep = new FilterStep(new NudeFilterBuilder(removed)
				.addFilterStage(new FilterStageBuilder(removed)
						.buildFilterStage())
				.buildFilter());
		
		steps.add(removeFIDFilterStep);
		
		Plan plan = new Plan(steps);
		
		ResultSet runStep = Plan.runPlan(plan);
		TableResponse tr = new TableResponse(runStep);
		StreamResponse sr = null;
		try {
			sr = Dispatcher.buildFormattedResponse(MimeType.CSV, tr);
		} catch (IOException| SQLException | XMLStreamException ex) {
			ex.printStackTrace();
		}
		if (sr != null && output != null) {
			// TODO modify dispatcher to slowly add header information to this outputstream
			// output.write(DescriptionLoaderSingleton.getDescription(SITE_ATTRIBUTE_TITLE).getBytes());
			StreamResponse.dispatch(sr, new PrintWriter(output));
			output.flush();
		}
	}
	
	private static String buildSiteInfoRequest(final Map<String, String[]> params) {
		//TODO build OGC filter
		String filter = "";
		return JNDISingleton.getInstance().getProperty(SITE_INFO_URL_JNDI_NAME) + 
				"?service=WFS&version=1.0.0&request=GetFeature&typeName=" + SITE_LAYER_NAME + "&outputFormat=csv" + filter;
	}
}
