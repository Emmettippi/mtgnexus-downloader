package it.mtgnexus.downloader;

import static it.mtgnexus.downloader.util.Constants.CFG_ORACLE;
import static it.mtgnexus.downloader.util.Constants.CFG_SIZE;
import static it.mtgnexus.downloader.util.Constants.CFG_URL;
import static it.mtgnexus.downloader.util.Constants.DOWNLOAD_FOLDER;
import static it.mtgnexus.downloader.util.Constants.EQUAL;
import static it.mtgnexus.downloader.util.Constants.HTTPS_MAGIC_NEXUS_COM;
import static it.mtgnexus.downloader.util.Constants.ccc_image_text_wrap;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.FileSystems;
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

		File downloadFolder = new File(DOWNLOAD_FOLDER);
		if (!downloadFolder.isDirectory()) {
			downloadFolder.mkdirs();
		}

		restTemplate = new RestTemplateBuilder().build();
		continueDownloading = true;

		int page = 0;
		String list = oracle ? "oracle" : "images";
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
			setName = htmlPage.split("</span>")[0];

			System.out.println(setName);

			File setFolder = new File(DOWNLOAD_FOLDER + SEPARATOR + setName);
			if (!setFolder.isDirectory()) {
				setFolder.mkdirs();
			}

			while (htmlPage.indexOf(ccc_image_text_wrap) != -1) {
				htmlPage = htmlPage.substring(
					htmlPage.indexOf(ccc_image_text_wrap) + ccc_image_text_wrap.length());
				downloadCards(htmlPage.split("cdb_pagination_results")[0].split(ccc_image_text_wrap)[0]);
				htmlPage = htmlPage.substring(ccc_image_text_wrap.length());
			}
		}
	}

	public static void downloadCards(String currentHtmlCard) {
		if (currentHtmlCard.contains("ccc_card_image_frontside")) {
			String frontSide = currentHtmlCard.split("ccc_card_image_frontside")[1].split("ccc_card_image_backside")[0];
			String backSide = currentHtmlCard.split("ccc_card_image_backside")[1];
		} else {
			downloadCard(currentHtmlCard, DOWNLOAD_FOLDER + SEPARATOR + setName);
		}
	}

	public static void downloadCard(String currentHtmlCard, String folder) {
		String cardName = currentHtmlCard.split("<img title=\"")[1].split("\" src=\"/img")[0];
		String imgId = currentHtmlCard.split("src=\"/img/ccc/ren/")[1].split("/")[1].split(".jpg")[0];
		String imgUrl = currentHtmlCard.split("src=\"")[1].split("\\?t")[0];

		File file = restTemplate.execute(HTTPS_MAGIC_NEXUS_COM + imgUrl, HttpMethod.GET, null, response -> {
			String fileName = imgId + " " + cardName + ".jpg";
			File ret = new File(folder + SEPARATOR + fileName);
			FileOutputStream fos = new FileOutputStream(ret);
			InputStream is = new BufferedInputStream(response.getBody());
			StreamUtils.copy(is, fos);
			is.close();
			fos.close();
			return ret;
		});
		System.out.println("file created: " + file.getName());
		continueDownloading = true;
	}
}
