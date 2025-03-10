package it.mtgnexus.downloader.jasper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.util.JRElementsVisitor;
import net.sf.jasperreports.engine.util.JRSaver;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;

public class JasperReportProducer {

	public static final String JASPER_SOURCE = "jasper\\MtgCardJasper.jrxml";
	public static final String JASPER_COMPILED = "jasper\\MtgCardJasper.jasper";

	public byte[] generateReport(Map<String, Object> parameters, Collection<?> items)
		throws JRException, IOException {
		compileReport();
		JasperPrint jasperPrint = createJasperPrint(items, parameters);
		return createPdf(jasperPrint);
	}

	private byte[] createPdf(JasperPrint jasperPrint) throws IOException, JRException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JRPdfExporter exporter = new JRPdfExporter();
		exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(baos));
		exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
		exporter.exportReport();
		baos.flush();
		return baos.toByteArray();
	}

	private JasperPrint createJasperPrint(Collection<?> items, Map<String, Object> parameters)
		throws JRException {
		JRBeanCollectionDataSource datasource = new JRBeanCollectionDataSource(items);
		return JasperFillManager.fillReport(JASPER_COMPILED, parameters, datasource);
	}

	private void compileReport() throws JRException {
		File fileReport = new File(JASPER_COMPILED);
		if (!fileReport.exists()) {
			System.out.println("Compiling:" + JASPER_SOURCE);
			JasperReport report = JasperCompileManager.compileReport(JASPER_SOURCE);
			JRElementsVisitor.visitReport(report, new SubReportVisitor("jasper"));
			JRSaver.saveObject(report, JASPER_COMPILED);
		}
	}
}
