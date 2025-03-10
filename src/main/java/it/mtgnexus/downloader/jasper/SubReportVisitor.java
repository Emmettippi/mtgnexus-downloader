package it.mtgnexus.downloader.jasper;

import org.apache.commons.io.FilenameUtils;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRSubreport;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.util.JRElementsVisitor;
import net.sf.jasperreports.engine.util.JRSaver;
import net.sf.jasperreports.engine.util.JRVisitorSupport;

public class SubReportVisitor extends JRVisitorSupport {

	private final String jasperPath;

	public SubReportVisitor(String jasperPath) {
		this.jasperPath = jasperPath;
	}

	@Override
	public void visitSubreport(JRSubreport subreport) {
		SubReportInfo subReportInfo = new SubReportInfo(subreport, jasperPath);
		this.compile(subReportInfo);
	}

	private void compile(SubReportInfo subReportInfo) {
		try {
			if (subReportInfo.shouldCompile()) {
				System.out.println("Compiling subreport \"" + subReportInfo.getJRXMLFilename() + "\"");
				JasperReport report = JasperCompileManager.compileReport(subReportInfo.getJRXMLFilepath());
				JRElementsVisitor.visitReport(report,
					new SubReportVisitor(retrievePathReport(subReportInfo.getJRXMLFilepath())));
				JRSaver.saveObject(report, subReportInfo.getJasperFilepath());
			}
		} catch (JRException e) {
			throw new IllegalStateException("Can't compile subreport \"" + subReportInfo.getJRXMLFilename() + "\"", e);
		}
	}

	private String retrievePathReport(String reportName) {
		return FilenameUtils.getFullPath(reportName);
	}
}