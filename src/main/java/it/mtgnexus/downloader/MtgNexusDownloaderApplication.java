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

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import it.mtgnexus.downloader.util.Constants;
import it.mtgnexus.downloader.util.InputReader;

public class MtgNexusDownloaderApplication {

	static String url;
	static boolean oracle;
	static int pageSize;

	static final List<Integer> allowedPages = Arrays.asList(24, 48, 100, 200, 400);
	static RestTemplate restTemplate;
	static String setName;
	static boolean continueDownloading = true;
	static final String SEPARATOR = FileSystems.getDefault().getSeparator();

	static List<String> allCards = new ArrayList<>();

	public static void main(String[] args) {
		File configFile = new File(Constants.INPUT_FILE);
		String propertiesInLine = InputReader.readPlainTextFile(configFile);
		String[] cfgs = propertiesInLine.split("\n");
		Map<String, Integer> cardPrintingQuantity = new HashMap<>();
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

			if (cfg.matches("\\d+ .+")) {
				int quantity = Integer.parseInt(cfg.split(" ")[0]);
				String cardName = cfg.substring(cfg.indexOf(" ")).trim();
				cardPrintingQuantity.put(cardName, quantity);
			}
		}

		String downloadFolderPath = DOWNLOAD_FOLDER;
		File downloadFolder = new File(downloadFolderPath);
		if (!downloadFolder.isDirectory()) {
			downloadFolder.mkdirs();
		}
		String setFolderPath = "";

		restTemplate = new RestTemplateBuilder().build();
		continueDownloading = true;

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
				downloadCards(htmlPage.split("cdb_pagination_results")[0].split(htmlTextSeparator)[0], setFolderPath);
				htmlPage = htmlPage.substring(htmlTextSeparator.length());
			}
		}

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
	}

	public static void downloadCards(String currentHtmlCard, String setFolderPath) {
		if (currentHtmlCard.contains(ccc_card_image_frontside)) {
			String frontSide = currentHtmlCard.split(ccc_card_image_frontside)[1].split(ccc_card_image_backside)[0];
			String backSide = currentHtmlCard.split(ccc_card_image_backside)[1];
			String imgId = currentHtmlCard.split("id=\"card_img_")[1].split("\" style=\"")[0];

			String flipFolderPath = setFolderPath + SEPARATOR + imgId;
			File flipFolder = new File(flipFolderPath);
			if (!flipFolder.isDirectory()) {
				flipFolder.mkdirs();
			}
			downloadCard(frontSide, flipFolderPath);
			downloadCard(backSide, flipFolderPath);
		} else {
			downloadCard(currentHtmlCard, setFolderPath);
		}
	}

	public static void downloadCard(String currentHtmlCard, String folderPath) {
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
		System.out.println("file created: " + file.getName());
		continueDownloading = true;

		allCards.add((allCards.size() + 1) + " [] " + cardName);
	}

	public static String sanitizeFileName(String name) {
		return name.replaceAll("[^a-zA-Z0-9\\.,'_\\-\\!]+", " ");
	}
}
