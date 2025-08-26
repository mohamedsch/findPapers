package org.example;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.List;

import com.itextpdf.text.*;
import com.itextpdf.text.Font;
import org.json.JSONObject;
import org.json.JSONArray;

import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import javax.net.ssl.*;


public class Main {


    static String SPRINGER_API_KEY = "**********************";
    static String ELSEVIER_API_KEY = "**********************";
    static String IEEE_API_KEY = "**********************";
    static int PAGE_SIZE = 20;
    static String q = "";

    public static void main(String[] args) {
        try {


            Map<String, String> params = new HashMap<>();

            // Simple parser: look for "-flag value"
            for (int i = 0; i < args.length - 1; i++) {
                if (args[i].startsWith("-")) {
                    params.put(args[i].substring(1), args[i + 1]); // remove "-"
                }
            }

            if (params.size() > 3) {
                SPRINGER_API_KEY = params.getOrDefault("springerApi", "");
                ELSEVIER_API_KEY = params.getOrDefault("elsevierApi", "");
                IEEE_API_KEY = params.getOrDefault("ieeeApi", "");
                PAGE_SIZE = Integer.parseInt(params.getOrDefault("pageSize", "20"));
                q = params.getOrDefault("query", "");
            }
            // Extract values with defaults


          /*  System.setProperty("http.proxyHost", "**");
            System.setProperty("http.proxyPort", "**");
            System.setProperty("https.proxyHost", "**");
            System.setProperty("https.proxyPort", "**");

            System.setProperty("http.proxyUser", "***");
            System.setProperty("http.proxyPassword", "***");

            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication("****", "***".toCharArray());
                }
            });
            SslUtil.disableSslVerification();*/

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

            // 5. Export
            exportToPdf(allArticles, "AllArticles.pdf");
            System.out.println("Exported " + allArticles.size() + " articles to AllArticles.pdf");


            //Checking duplicated

            Map<String, List<Article>> doiMap = new HashMap<>();

            for (Article article : allArticles) {
                if (article.doi != null && !article.doi.isEmpty()) {
                    doiMap.computeIfAbsent(article.doi, k -> new ArrayList<>()).add(article);
                }
            }

            for (Map.Entry<String, List<Article>> entry : doiMap.entrySet()) {
                if (entry.getValue().size() > 1) {
                    System.out.println("Duplicate DOI: " + entry.getKey());
                    for (Article a : entry.getValue()) {
                        System.out.println("  Source: " + a.source + " | Title: " + a.title);
                    }
                }
            }

            Map<String, Map<String, Integer>> duplicateCounts = new HashMap<>();

            for (List<Article> group : doiMap.values()) {
                if (group.size() < 2) continue; // Skip non-duplicates

                // Count all pairwise combinations for this DOI
                for (int i = 0; i < group.size(); i++) {
                    for (int j = 0; j < group.size(); j++) {
                        String sourceA = group.get(i).source;
                        String sourceB = group.get(j).source;

                        // or use: group.get(i).getSource() depending on your class
                        // or extract from Article object accordingly

                        duplicateCounts.computeIfAbsent(sourceA, k -> new HashMap<>());
                        duplicateCounts.get(sourceA).merge(sourceB, 1, Integer::sum);
                    }
                }
            }
            // Collect all unique sources
            Set<String> sources = duplicateCounts.keySet();

// Print header row
            System.out.print("          | ");
            for (String src : sources) {
                System.out.printf("%-15s", src);
            }
            System.out.println("\n-------------------------------------------------------------------------------------------------------------------");

// Print each row
            for (String srcA : sources) {
                System.out.printf("%-9s | ", srcA);
                for (String srcB : sources) {
                    int count = duplicateCounts.getOrDefault(srcA, new HashMap<>()).getOrDefault(srcB, 0);
                    System.out.printf("%-15d", count);
                }
                System.out.println();
            }

            //


            Map<String, Article> uniqueByDoi = new HashMap<>();
            int cptEmpty = 0;
            int cptDup = 0;
            for (Article article : allArticles) {
                String doi = article.getDoi();
                if (doi == null || doi.isEmpty()) {
                    cptEmpty++;
                    continue;
                }

                if (uniqueByDoi.containsKey(doi)) {
                    cptDup++;
                    // Merge sources if DOI already exists
                    Article existing = uniqueByDoi.get(doi);
                    String existingSource = existing.getSource();
                    String newSource = article.getSource();

                    // Add new source if not already present
                    Set<String> mergedSources = new LinkedHashSet<>(Arrays.asList(existingSource.split(",")));
                    mergedSources.addAll(Arrays.asList(newSource.split(",")));

                    existing.setSource(String.join(",", mergedSources));
                } else {
                    uniqueByDoi.put(doi, article);
                }
            }
            System.out.println("NBR empty : " + cptEmpty);
            System.out.println("NBR dup : " + cptDup);

            List<Article> deduplicatedArticles = new ArrayList<>(uniqueByDoi.values());

            exportToPdf(deduplicatedArticles, "AllArticles-removeDup.pdf");
            System.out.println("Exported " + deduplicatedArticles.size() + " articles to AllArticles-removeDup.pdf after removing duplications");


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static List<Article> fetchFromSpringer() throws Exception {
        List<Article> articles = new ArrayList<>();
        String query = "((\"System of Systems\" OR \"Systems of Systems\" OR \"System-of-Systems\" OR \"Systems-of-Systems\" ) AND " + "(\"security\" OR \"risk assessment\" OR \"risk management\" )) AND " + "(datefrom:2015-01-01 and dateto:2025-05-01) AND type:{(Journal)}";
        if (!q.isEmpty()) query = QueryBuilder.forSpringer(q);
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
        String query = "TITLE-ABS-KEY((\"System of Systems\" OR \"Systems of Systems\" OR \"System-of-Systems\" OR \"Systems-of-Systems\") AND " + "(\"security\" OR \"risk assessment\" OR \"risk management\")) AND PUBYEAR > 2014";
        if (!q.isEmpty()) query = QueryBuilder.forElsevier(q);
        String encodedQuery = URLEncoder.encode(query, "UTF-8");

        String[] sources = {"https://api.elsevier.com/content/search/scopus",
                //    "https://api.elsevier.com/content/search/sciencedirect"
        };

        for (String baseUrl : sources) {
            int start = 0;
            int count = 25;  // max 25 per page for most Elsevier endpoints

            boolean more = true;
            while (more) {
                String urlStr = String.format("%s?query=%s&start=%d&count=%d&apiKey=%s", baseUrl, encodedQuery, start, count, ELSEVIER_API_KEY);

                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");

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
                        String doi = entry.optString("prism:doi");


                        String title = entry.optString("dc:title");
                        String abs = entry.optString("dc:description");
                        String authors = entry.optString("dc:creator");
                        String pdfUrl = entry.optString("link"); // often doesn't contain direct PDF unless entitled

                        String source = baseUrl.contains("scopus") ? "Scopus" : "ScienceDirect";
                        articles.add(new Article(title, authors, doi, abs, pdfUrl, source));
                    }

                    start += count;
                    more = entries.length() == count;
                } else {
                    System.err.println("Elsevier API Error (" + baseUrl + "): " + conn.getResponseCode());
                    more = false;
                }

                conn.disconnect();
            }
        }

        return articles;
    }

    static List<Article> fetchFromIEEE() throws Exception {
        List<Article> articles = new ArrayList<>();
        String query = "(\"System of Systems\" OR \"Systems of Systems\" OR \"System-of-Systems\" OR \"Systems-of-Systems\") AND " + "(\"security\" OR \"risk assessment\" OR \"risk management\")";
        if (!q.isEmpty()) query = QueryBuilder.forIEEE(q);
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

    static List<Article> fetchFromCrossref() throws Exception {
        List<Article> articles = new ArrayList<>();
        String query = "System of Systems Systems-of-Systems security risk assessment risk management";
        if (!q.isEmpty()) query = QueryBuilder.forCrossref(q);
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

        int rows = 100; // Max allowed per page
        String cursor = "*"; // Start with initial cursor
        boolean hasMoreResults = true;

        while (hasMoreResults && articles.size() < 1500) {
            String urlStr = String.format(
                    "https://api.crossref.org/works?query=%s&rows=%d&cursor=%s&sort=relevance",
                    encodedQuery, rows, cursor
            );

            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "MyResearchApp/1.0 (mailto:moscherchar@gmail.com)");

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

                for (int i = 0; i < numResults && articles.size() < 1500; i++) {
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

class SslUtil {
    public static void disableSslVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }};

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class QueryBuilder {

    static String forSpringer(String userQuery) {
        return String.format("(%s) AND (datefrom:2015-01-01 and dateto:2025-05-01) AND type:{(Journal)}", userQuery);
    }

    static String forElsevier(String userQuery) {
        return String.format("TITLE-ABS-KEY(%s) AND PUBYEAR > 2015", userQuery);
    }

    static String forIEEE(String userQuery) {
        return String.format("(%s)", userQuery); // IEEE supports AND/OR in plain text
    }

    static String forCrossref(String userQuery) {
        return userQuery; // Crossref accepts free text
    }
}
