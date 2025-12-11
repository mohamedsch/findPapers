package org.findp;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

import com.itextpdf.text.*;
import com.itextpdf.text.Font;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONObject;
import org.json.JSONArray;

import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;



public class Main {


    static final String SPRINGER_API_KEY = "**********************";
    static final String ELSEVIER_API_KEY = "**********************";
    static final String IEEE_API_KEY = "**********************";
    static final String WOS_API_KEY = "**********************";
    static final int PAGE_SIZE = 20;

    public static void main(String[] args) {
        try {


            List<Article> allArticles = new ArrayList<>();

            // 1. CrossRef
            List<Article> cross = fetchFromCrossref();
            System.out.println("CrossRef: " + cross.size() + " articles");
            allArticles.addAll(cross);

            // 2. Springer
            List<Article> springer = fetchFromSpringer();
            System.out.println("SpringerLink: " + springer.size() + " articles");
            allArticles.addAll(springer);

            // 3. Scopus
            List<Article> scopus = fetchFromElsevier();
            System.out.println("Scopus: " + scopus.size() + " articles");
            allArticles.addAll(scopus);

            // 4. IEEE
            List<Article> ieee = fetchFromIEEE();
            System.out.println("IEEE Xplore: " + ieee.size() + " articles");
            allArticles.addAll(ieee);

            // 5. WoS
            List<Article> wos = fetchFromWOS();
            System.out.println("WoS: " + wos.size() + " articles");
            allArticles.addAll(wos);

            // 6. Export
            exportToPdf(allArticles, "AllArticles.pdf");
            exportToExcel(allArticles, "AllArticles.xlsx");
            System.out.println("Exported " + allArticles.size() + " articles to AllArticles.pdf");


            // Checking duplicated using DOI only
            Map<String, List<Article>> doiMap = new HashMap<>();

            for (Article article : allArticles) {
                if (article.doi != null && !article.doi.trim().isEmpty()) {
                    String doiKey = article.doi.trim().toLowerCase(); // normalize
                    doiMap.computeIfAbsent(doiKey, k -> new ArrayList<>()).add(article);
                }
            }

// Print duplicates
            for (Map.Entry<String, List<Article>> entry : doiMap.entrySet()) {
                if (entry.getValue().size() > 1) {
                    System.out.println("Duplicate DOI: " + entry.getKey());
                    for (Article a : entry.getValue()) {
                        System.out.println("  Source: " + a.source + " | Title: " + a.title);
                    }
                }
            }

// Counting duplicate relations between sources
            Map<String, Map<String, Integer>> duplicateCounts = new HashMap<>();

            for (List<Article> group : doiMap.values()) {
                if (group.size() < 2) continue;  // Only duplicates

                for (int i = 0; i < group.size(); i++) {
                    for (int j = 0; j < group.size(); j++) {

                        String srcA = group.get(i).source;
                        String srcB = group.get(j).source;

                        duplicateCounts
                                .computeIfAbsent(srcA, k -> new HashMap<>())
                                .merge(srcB, 1, Integer::sum);
                    }
                }
            }



        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    static List<Article> fetchFromSpringer() throws Exception {
        List<Article> articles = new ArrayList<>();
        String query = "((\"System of Systems\" OR \"Systems of Systems\" OR \"System-of-Systems\" OR \"Systems-of-Systems\" ) AND " + "(\"security\" OR \"risk assessment\" OR \"risk management\" )) AND " + "(datefrom:2015-01-01 and dateto:2025-12-31) AND type:{(Journal)}";
        String encodedQuery = URLEncoder.encode(query, "UTF-8");

        int start = 1;
        int total = Integer.MAX_VALUE;

        while (start <= total) {
            String urlStr = String.format("https://api.springernature.com/meta/v2/json?api_key=%s&q=%s&s=%d&p=%d", SPRINGER_API_KEY, encodedQuery, start, PAGE_SIZE);

            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == 200) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                }

                JSONObject json = new JSONObject(response.toString());
                JSONArray records = json.getJSONArray("records");

                if (total == Integer.MAX_VALUE) {
                    JSONObject result = json.getJSONArray("result").getJSONObject(0);
                    total = result.getInt("total");
                }

                for (int i = 0; i < records.length(); i++) {
                    JSONObject rec = records.getJSONObject(i);
                    String title = rec.optString("title");
                    String doi = rec.optString("doi");


                    String abs = rec.optString("abstract");
                    JSONArray creators = rec.optJSONArray("creators");
                    StringBuilder authors = new StringBuilder();
                    if (creators != null) {
                        for (int j = 0; j < creators.length(); j++) {
                            authors.append(creators.getJSONObject(j).optString("creator")).append(", ");
                        }
                    }

                    String pdfUrl = "";
                    JSONArray urls = rec.optJSONArray("url");
                    if (urls != null) {
                        for (int j = 0; j < urls.length(); j++) {
                            JSONObject u = urls.getJSONObject(j);
                            if ("pdf".equalsIgnoreCase(u.optString("format"))) {
                                pdfUrl = u.optString("value");
                                break;
                            }
                        }
                    }

                    articles.add(new Article(title, authors.toString(), doi, abs, pdfUrl, "SpringerLink"));
                }

                start += PAGE_SIZE;
            } else {
                System.err.println("Springer API Error: " + conn.getResponseCode());
                break;
            }

            conn.disconnect();
        }

        return articles;
    }
    static List<Article> fetchFromElsevier() throws Exception {
        List<Article> articles = new ArrayList<>();

        String query =
                "(" +
                        "TITLE-ABS-KEY(\"System of Systems\") OR " +
                        "TITLE-ABS-KEY(\"Systems of Systems\") OR " +
                        "TITLE-ABS-KEY(\"System-of-Systems\") OR " +
                        "TITLE-ABS-KEY(\"Systems-of-Systems\")" +
                        ") AND (" +
                        "TITLE-ABS-KEY(\"security\") OR " +
                        "TITLE-ABS-KEY(\"risk assessment\") OR " +
                        "TITLE-ABS-KEY(\"risk management\")" +
                        ") AND PUBYEAR > 2014";

        String encodedQuery = URLEncoder.encode(query, "UTF-8");

        String baseUrl = "https://api.elsevier.com/content/search/scopus";

        int start = 0;
        int count = 25;

        boolean more = true;
        while (more) {

            String urlStr = String.format(
                    "%s?query=%s&start=%d&count=%d&apiKey=%s&httpAccept=application/json",
                    baseUrl, encodedQuery, start, count, ELSEVIER_API_KEY
            );

            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == 200) {

                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                }

                JSONObject json = new JSONObject(response.toString());
                JSONArray entries = json.optJSONObject("search-results").optJSONArray("entry");

                if (entries == null || entries.length() == 0) break;

                for (int i = 0; i < entries.length(); i++) {
                    JSONObject entry = entries.getJSONObject(i);

                    String title = entry.optString("dc:title", "");
                    String abs = entry.optString("dc:description", "");
                    String doi = entry.optString("prism:doi", "");

                    // Authors: sometimes object, sometimes array
                    String authors = "";
                    Object creator = entry.opt("dc:creator");
                    if (creator instanceof JSONArray) {
                        StringBuilder builder = new StringBuilder();
                        JSONArray arr = (JSONArray) creator;
                        for (int a = 0; a < arr.length(); a++) builder.append(arr.getString(a)).append(", ");
                        authors = builder.toString();
                    } else if (creator instanceof String) {
                        authors = creator.toString();
                    }

                    // Links (pdf not available, but return the scopus link)
                    String pdfUrl = "";
                    JSONArray links = entry.optJSONArray("link");
                    if (links != null) {
                        for (int l = 0; l < links.length(); l++) {
                            JSONObject link = links.getJSONObject(l);
                            if ("scopus".equalsIgnoreCase(link.optString("@ref"))) {
                                pdfUrl = link.optString("@href");
                            }
                        }
                    }

                    articles.add(new Article(title, authors, doi, abs, pdfUrl, "Scopus"));
                }

                start += count;
                more = entries.length() == count;

            } else {
                System.err.println("Elsevier API Error (Scopus): " + conn.getResponseCode());
                more = false;
            }

            conn.disconnect();
        }

        return articles;
    }

    static List<Article> fetchFromIEEE() throws Exception {
        List<Article> articles = new ArrayList<>();
        String query = "(\"System of Systems\" OR \"Systems of Systems\" OR \"System-of-Systems\" OR \"Systems-of-Systems\") AND " + "(\"security\" OR \"risk assessment\" OR \"risk management\")";
        String encodedQuery = URLEncoder.encode(query, "UTF-8");

        int startRecord = 1;
        int maxRecords = 25;
        boolean moreResults = true;

        while (moreResults) {
            String urlStr = String.format("https://ieeexploreapi.ieee.org/api/v1/search/articles?apikey=%s&querytext=%s&start_record=%d&max_records=%d&sort_order=asc&start_year=2015&end_year=2025", IEEE_API_KEY, encodedQuery, startRecord, maxRecords);

            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == 200) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                }

                JSONObject json = new JSONObject(response.toString());
                JSONArray articlesArray = json.optJSONArray("articles");

                if (articlesArray == null || articlesArray.length() == 0) {
                    moreResults = false;
                    break;
                }

                for (int i = 0; i < articlesArray.length(); i++) {
                    JSONObject rec = articlesArray.getJSONObject(i);

                    String title = rec.optString("title");
                    String doi = rec.optString("doi");
                    String abs = rec.optString("abstract");

                    JSONArray authorsArray = rec.optJSONArray("authors");
                    StringBuilder authors = new StringBuilder();
                    if (authorsArray != null) {
                        for (int j = 0; j < authorsArray.length(); j++) {
                            JSONObject author = authorsArray.getJSONObject(j);
                            authors.append(author.optString("full_name")).append(", ");
                        }
                    }

                    String pdfUrl = rec.optString("pdf_url");

                    articles.add(new Article(title, authors.toString(), doi, abs, pdfUrl, "IEEE Xplore"));
                }

                startRecord += maxRecords;
                int totalRecords = json.optInt("total_records");
                if (startRecord > totalRecords) {
                    moreResults = false;
                }

            } else {
                System.err.println("IEEE API Error: " + conn.getResponseCode());
                break;
            }

            conn.disconnect();
        }

        return articles;
    }

    static List<Article> fetchFromWOS() throws Exception {

        List<Article> articles = new ArrayList<>();

        String query =
                "TS=(\"System of Systems\" OR \"Systems of Systems\" OR \"System-of-Systems\" OR \"Systems-of-Systems\")" +
                        " AND TS=(\"security\" OR \"risk assessment\" OR \"risk management\")" +
                        " AND PY=(2015-2025)";

        String encodedQuery = URLEncoder.encode(query, "UTF-8");

        int page = 1;
        int limit = 50;
        boolean more = true;

        while (more) {

            String urlStr = String.format(
                    "https://api.clarivate.com/apis/wos-starter/v1/documents?q=%s&page=%d&limit=%d",
                    encodedQuery, page, limit
            );

            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-ApiKey", WOS_API_KEY);

            int code = conn.getResponseCode();

            if (code == 200) {

                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) response.append(line);
                }

                JSONObject json = new JSONObject(response.toString());
                JSONArray hits = json.optJSONArray("hits");

                if (hits == null || hits.length() == 0) break;

                for (int i = 0; i < hits.length(); i++) {
                    JSONObject rec = hits.getJSONObject(i);

                    // ---- TITLE ----
                    String title = rec.optString("title", "");

                    // ---- DOI ----
                    JSONObject ids = rec.optJSONObject("identifiers");
                    String doi = ids != null ? ids.optString("doi", "") : "";

                    // ---- AUTHORS ----
                    StringBuilder authors = new StringBuilder();
                    JSONObject names = rec.optJSONObject("names");
                    if (names != null) {
                        JSONArray auth = names.optJSONArray("authors");
                        if (auth != null) {
                            for (int a = 0; a < auth.length(); a++) {
                                JSONObject person = auth.getJSONObject(a);
                                String name = person.optString("displayName",
                                        person.optString("wosStandard", "")
                                );
                                if (!name.isEmpty()) {
                                    authors.append(name).append(", ");
                                }
                            }
                        }
                    }
                    // remove last comma
                    String authorStr = authors.length() > 2 ?
                            authors.substring(0, authors.length() - 2) : "";

                    // ---- ABSTRACT ----
                    // NOTE: WOS Starter API does NOT provide abstracts
                    String abs = "";

                    articles.add(new Article(
                            title,
                            authorStr,
                            doi,
                            abs,
                            "",
                            "Web of Science"
                    ));
                }

                int total = json.getJSONObject("metadata").getInt("total");
                int maxPages = (int) Math.ceil(total / (double) limit);

                page++;
                if (page > maxPages) more = false;

            } else {
                System.err.println("WOS API Error: " + code);
                break;
            }

            conn.disconnect();
            Thread.sleep(2000);
        }

        return articles;
    }

    static List<Article> fetchFromCrossref() throws Exception {
        List<Article> articles = new ArrayList<>();
        String query = "System of Systems Systems of Systems System-of-Systems Systems-of-Systems security risk assessment risk management";
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

        int rows = 100; // Max allowed per page
        String cursor = "*"; // Start with initial cursor
        boolean hasMoreResults = true;

        while (hasMoreResults && articles.size() < 1000) {
            String urlStr = String.format(
                    "https://api.crossref.org/works?filter=prefix:10.1145,from-pub-date:2015-01-01,until-pub-date:2025-12-31&query=%s&rows=%d&cursor=%s&sort=relevance",
                    encodedQuery, rows, cursor
            );

            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "MyResearchApp/1.0 (mailto:mail@gmail.com)");

            if (conn.getResponseCode() == 200) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                JSONObject json = new JSONObject(response.toString());
                JSONObject message = json.getJSONObject("message");

                JSONArray items = message.getJSONArray("items");
                int numResults = items.length();

                if (numResults == 0) {
                    hasMoreResults = false;
                    break;
                }

                for (int i = 0; i < numResults && articles.size() < 1000; i++) {
                    JSONObject rec = items.getJSONObject(i);

                    String title = rec.optJSONArray("title") != null ? rec.getJSONArray("title").optString(0) : "";
                    String doi = rec.optString("DOI");
                    String abs = rec.optString("abstract");

                    StringBuilder authors = new StringBuilder();
                    JSONArray authorArray = rec.optJSONArray("author");
                    if (authorArray != null) {
                        for (int j = 0; j < authorArray.length(); j++) {
                            JSONObject author = authorArray.getJSONObject(j);
                            String given = author.optString("given");
                            String family = author.optString("family");
                            if (!given.isEmpty() || !family.isEmpty()) {
                                authors.append(given).append(" ").append(family).append(", ");
                            }
                        }
                    }

                    String pdfUrl = "";
                    JSONArray links = rec.optJSONArray("link");
                    if (links != null) {
                        for (int j = 0; j < links.length(); j++) {
                            JSONObject link = links.getJSONObject(j);
                            if ("application/pdf".equals(link.optString("content-type"))) {
                                pdfUrl = link.optString("URL");
                                break;
                            }
                        }
                    }

                    articles.add(new Article(title, authors.toString(), doi, abs, pdfUrl, "Crossref"));
                }

                // Get next cursor
                cursor = message.optString("next-cursor", null);
                if (cursor == null || cursor.isEmpty()) {
                    hasMoreResults = false;
                }

                Thread.sleep(100); // Be polite and avoid rate limits

            } else {
                System.err.println("Crossref API Error: " + conn.getResponseCode());
                hasMoreResults = false;
            }

            conn.disconnect();
        }

        return articles;
    }

    static void exportToPdf(List<Article> articles, String fileName) throws Exception {
        Document doc = new Document();
        PdfWriter.getInstance(doc, new FileOutputStream(fileName));
        doc.open();

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Paragraph title = new Paragraph("Consolidated Article List", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        doc.add(title);

        PdfPTable table = new PdfPTable(6); // 5 columns now
        table.setWidths(new float[]{3, 2, 2, 2, 2, 1.5f});
        table.setWidthPercentage(100);

        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 10);


        table.addCell(new PdfPCell(new Phrase("Title", headerFont)));
        table.addCell(new PdfPCell(new Phrase("Authors", headerFont)));
        table.addCell(new PdfPCell(new Phrase("Abstract", headerFont)));
        table.addCell(new PdfPCell(new Phrase("DOI", headerFont)));
        table.addCell(new PdfPCell(new Phrase("PDF Link", headerFont)));
        table.addCell(new PdfPCell(new Phrase("Source", headerFont)));


        for (Article article : articles) {
            table.addCell(new PdfPCell(new Phrase(article.title, cellFont)));
            table.addCell(new PdfPCell(new Phrase(article.authors, cellFont)));
            table.addCell(new PdfPCell(new Phrase(article.abstractText, cellFont)));
            table.addCell(new PdfPCell(new Phrase(article.doi, cellFont)));
            table.addCell(new PdfPCell(new Phrase(article.pdfUrl, cellFont)));
            table.addCell(new PdfPCell(new Phrase(article.source, cellFont)));
        }

        doc.add(table);
        Paragraph total = new Paragraph("\nTotal Articles: " + articles.size(), headerFont);
        total.setAlignment(Element.ALIGN_RIGHT);
        doc.add(total);

        doc.close();
    }

    static void exportToExcel(List<Article> articles, String fileName) throws Exception {

        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Articles");

        // Create header style
        CellStyle headerStyle = workbook.createCellStyle();
        XSSFFont headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeight(12);
        headerStyle.setFont(headerFont);

        // Create cell style
        CellStyle cellStyle = workbook.createCellStyle();
        XSSFFont bodyFont = workbook.createFont();
        bodyFont.setFontHeight(10);
        cellStyle.setFont(bodyFont);

        // Header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Title", "Authors", "Abstract", "DOI", "PDF Link", "Source"};

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data rows
        int rowIndex = 1;
        for (Article article : articles) {
            Row row = sheet.createRow(rowIndex++);

            row.createCell(0).setCellValue(article.title);
            row.createCell(1).setCellValue(article.authors);
            row.createCell(2).setCellValue(article.abstractText);
            row.createCell(3).setCellValue(article.doi);
            row.createCell(4).setCellValue(article.pdfUrl);
            row.createCell(5).setCellValue(article.source);

            for (int i = 0; i < headers.length; i++) {
                row.getCell(i).setCellStyle(cellStyle);
            }
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        // Write file
        FileOutputStream fos = new FileOutputStream(fileName);
        workbook.write(fos);
        fos.close();
        workbook.close();
    }


    static class Article {
        String title;
        String authors;
        String doi;
        String abstractText;
        String pdfUrl;
        String source;

        Article(String title, String authors, String doi, String abstractText, String pdfUrl, String source) {
            this.title = title;
            this.authors = authors;
            this.doi = doi;
            this.abstractText = abstractText;
            this.pdfUrl = pdfUrl;
            this.source = source;
        }

        public String getDoi() {
            return doi;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

    }

}

