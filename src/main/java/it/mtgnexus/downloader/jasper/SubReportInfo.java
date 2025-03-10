package it.mtgnexus.downloader.jasper;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.jasperreports.engine.JRSubreport;

public class SubReportInfo {

	private final static Pattern FILENAME_PATTERN = Pattern.compile("\"(.*?.jasper)");

	private final File sourceSubreport;
	private final File compiledSubreport;

	public SubReportInfo(JRSubreport subreport, String jasperPath) {
		String filename = extractFilename(subreport);
		this.compiledSubreport = new File(jasperPath, filename);
		this.sourceSubreport = new File(jasperPath, filename.replaceAll(".jasper", ".jrxml"));
	}

	public String getJRXMLFilename() {
		return sourceSubreport.getName();
	}

	public String getJRXMLFilepath() {
		return sourceSubreport.getAbsolutePath();
	}

	public String getJasperFilename() {
		return compiledSubreport.getName();
	}

	public String getJasperFilepath() {
		return compiledSubreport.getAbsolutePath();
	}

	public boolean shouldCompile() {
		return !compiledSubreport.exists();

	}

	public String extractFilename(JRSubreport subreport) {
		String expression = subreport.getExpression().getText();
		Matcher matcher = FILENAME_PATTERN.matcher(expression);
		if (matcher.find()) {
			if (matcher.groupCount() > 0) {
				return matcher.group(1);
			}
		}
		throw new IllegalStateException("Unable to retrieve report \"" + expression + "\".");
	}
}