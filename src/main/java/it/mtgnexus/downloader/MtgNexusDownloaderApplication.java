package it.mtgnexus.downloader;

import static it.mtgnexus.downloader.util.Constants.CARD_LIST_FILE;
import static it.mtgnexus.downloader.util.Constants.CFG_ORACLE;
import static it.mtgnexus.downloader.util.Constants.CFG_SIZE;
import static it.mtgnexus.downloader.util.Constants.CFG_URL;
import static it.mtgnexus.downloader.util.Constants.DOWNLOAD_FOLDER;
import static it.mtgnexus.downloader.util.Constants.EQUAL;
import static it.mtgnexus.downloader.util.Constants.HTTPS_MAGIC_NEXUS_COM;
import static it.mtgnexus.downloader.util.Constants.MTGNEXUS_FOLDER;
import static it.mtgnexus.downloader.util.Constants.ccc_card_image_backside;
import static it.mtgnexus.downloader.util.Constants.ccc_card_image_frontside;
import static it.mtgnexus.downloader.util.Constants.ccc_image_text_wrap;
import static it.mtgnexus.downloader.util.Constants.ccc_side_by_side;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import it.mtgnexus.downloader.jasper.JasperReportProducer;
import it.mtgnexus.downloader.util.Constants;
import it.mtgnexus.downloader.util.ImgCardPath;
import it.mtgnexus.downloader.util.InputReader;

public class MtgNexusDownloaderApplication {

	static String url;
	static boolean oracle;
	static int pageSize;

	static final List<Integer> allowedPages = Arrays.asList(24, 48, 100, 200, 400);
	static RestTemplate restTemplate;
	static String setName;
	static final String SEPARATOR = FileSystems.getDefault().getSeparator();

	static List<String> allCards = new ArrayList<>();
	static List<ImgCardPath> allCardPaths = new ArrayList<>();
	static Map<String, Integer> cardPrintingQuantity = new HashMap<>();

	static JasperReportProducer jasperProducer = new JasperReportProducer();

	public static void main(String[] args) {
		File configFile = new File(Constants.INPUT_FILE);
		String propertiesInLine = InputReader.readPlainTextFile(configFile);
		String[] cfgs = propertiesInLine.split("\n");

		for (String cfg : cfgs) {
			if (cfg == null || cfg.trim().isEmpty()) {
				continue;
			}

			if (cfg.startsWith(CFG_URL)) {
				url = cfg.split(EQUAL)[1].trim().split("\\?")[0];
			}
			if (cfg.startsWith(CFG_ORACLE)) {
				String oracleStr = cfg.split(EQUAL)[1].trim();
				oracle = Boolean.parseBoolean(oracleStr);
			}
			if (cfg.startsWith(CFG_SIZE)) {
				String pageSizeStr = cfg.split(EQUAL)[1].trim();
				pageSize = Integer.parseInt(pageSizeStr);
				if (!allowedPages.contains(pageSize)) {
					pageSize = 24;
				}
			}

			if (cfg.matches("\\d+(a|b)? \\d+")) {
				String[] cardAndQuantity = cfg.split(" ");
				String cardId = cardAndQuantity[0];
				int quantity = Integer.parseInt(cardAndQuantity[1]);
				cardPrintingQuantity.put(cardId, quantity);
			}
		}

		String downloadFolderPath = DOWNLOAD_FOLDER;
		File downloadFolder = new File(downloadFolderPath);
		if (!downloadFolder.isDirectory()) {
			downloadFolder.mkdirs();
		}

		downloadCards(downloadFolderPath);

		createTxtCardList(downloadFolderPath);

		createPdf(downloadFolderPath);
	}

	public static void downloadCards(String downloadFolderPath) {
		String setFolderPath = "";
		restTemplate = new RestTemplateBuilder().build();
		boolean continueDownloading = true;

		int page = 0;
		String list = oracle ? "oracle" : "images";
		final String htmlTextSeparator = oracle ? ccc_side_by_side : ccc_image_text_wrap;
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("list", list);
		paramMap.put("pageSize", pageSize);

		while (continueDownloading) {
			continueDownloading = false;
			page++;
			System.out.println("Page: " + page);
			paramMap.put("page", page);
			String htmlPage = restTemplate
				.getForEntity(url + "?show=cards&list={list}&sort=set&p={page}&pp={pageSize}", String.class, paramMap)
				.getBody();

			htmlPage = htmlPage.split("<span itemprop=\"name\" property=\"name\">Custom Cards</span>")[1];
			htmlPage = htmlPage.split("<span itemprop=\"name\" property=\"name\">")[1];
			setName = sanitizeFileName(htmlPage.split("</span>")[0]);

			setFolderPath = downloadFolderPath + SEPARATOR + setName + SEPARATOR + MTGNEXUS_FOLDER;
			File setFolder = new File(setFolderPath);
			if (!setFolder.isDirectory()) {
				setFolder.mkdirs();
			}

			while (htmlPage.indexOf(htmlTextSeparator) != -1) {
				htmlPage = htmlPage.substring(
					htmlPage.indexOf(htmlTextSeparator) + htmlTextSeparator.length());
				downloadCard(htmlPage.split("cdb_pagination_results")[0].split(htmlTextSeparator)[0], setFolderPath);
				continueDownloading = true;
				htmlPage = htmlPage.substring(htmlTextSeparator.length());
			}
		}
		System.out.println("Cards downloaded. Find them at " + new File(setFolderPath).getAbsolutePath());
	}

	public static void downloadCard(String currentHtmlCard, String setFolderPath) {
		if (currentHtmlCard.contains(ccc_card_image_frontside)) {
			String frontSide = currentHtmlCard.split(ccc_card_image_frontside)[1].split(ccc_card_image_backside)[0];
			String backSide = currentHtmlCard.split(ccc_card_image_backside)[1];
			String imgId = currentHtmlCard.split("id=\"card_img_")[1].split("\" style=\"")[0];

			String flipFolderPath = setFolderPath + SEPARATOR + imgId;
			File flipFolder = new File(flipFolderPath);
			if (!flipFolder.isDirectory()) {
				flipFolder.mkdirs();
			}
			downloadSingleCard(frontSide, flipFolderPath);
			downloadSingleCard(backSide, flipFolderPath);
		} else {
			downloadSingleCard(currentHtmlCard, setFolderPath);
		}
	}

	public static void downloadSingleCard(String currentHtmlCard, String folderPath) {
		String cardName = currentHtmlCard.split("<img title=\"")[1].split("\" src=\"/img")[0];
		String imgId = currentHtmlCard.split("src=\"/img/ccc/ren/")[1].split("/")[1].split(".jpg")[0];
		String imgUrl = currentHtmlCard.split("src=\"")[1].split("\\?t")[0];

		File file = restTemplate.execute(HTTPS_MAGIC_NEXUS_COM + imgUrl, HttpMethod.GET, null, response -> {
			String fileName = imgId + " " + sanitizeFileName(cardName) + ".jpg";
			File ret = new File(folderPath + SEPARATOR + fileName);
			FileOutputStream fos = new FileOutputStream(ret);
			InputStream is = new BufferedInputStream(response.getBody());
			StreamUtils.copy(is, fos);
			is.close();
			fos.close();
			return ret;
		});
		System.out.println("File created: " + file.getName());

		allCards.add((allCards.size() + 1) + " [" + imgId + "] " + cardName);
		multiplyCardForSet(imgId, file);
	}

	public static void multiplyCardForSet(String imgId, File cardFile) {
		int times = ObjectUtils.firstNonNull(cardPrintingQuantity.get(imgId), 1);
		for (int i = 0; i < times; i++) {
			allCardPaths.add(new ImgCardPath(imgId, cardFile.getAbsolutePath()));
		}
	}

	public static String sanitizeFileName(String name) {
		return name.replaceAll("[^a-zA-Z0-9\\.,'_\\-\\!]+", " ");
	}

	public static void createTxtCardList(String downloadFolderPath) {
		File cardLister = new File(downloadFolderPath + SEPARATOR + setName + SEPARATOR + CARD_LIST_FILE);
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(cardLister));
			for (String card : allCards) {
				writer.write(card + "\n");
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Card list created. Find it at " + cardLister.getAbsolutePath());
	}

	public static void createPdf(String downloadFolderPath) {
		try {
			byte[] bytes = jasperProducer.generateReport(new HashMap<>(), allCardPaths);
			File pdfOutput = new File(downloadFolderPath + SEPARATOR + setName + SEPARATOR + setName + ".pdf");
			FileOutputStream outputStream = new FileOutputStream(pdfOutput);
			outputStream.write(bytes);
			outputStream.close();
			System.out.println("Pdf created. Find it at " + pdfOutput.getAbsolutePath());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
