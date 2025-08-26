# FindPapers

This project is a **Command Line Interface (CLI) tool** developed in **Java** for interacting with the **Springer,ELSEVIER,IEEE and CrossRef APIs**.  
It is part of a **Systematic Mapping Study (SMS)** and is designed to help researchers and developers fetch metadata, filter, and analyze scientific publications programmatically.

---

## 📖 Features

- Fetch publications from the **Springer,ELSEVIER,IEEE and CrossRef APIs**.
- Filter results based on keywords, dates, or publication type.
- Export data for further analysis in pdf formats.
- Designed for **systematic mapping studies** in research# Springer API CLI Tool

---

## ⚙️ Requirements

Before running the project, make sure you have:

- **Java 17+** installed
- **Maven** (if you want to build from source)
- An active **keys for all APIs** like :(sign up at [Springer API Portal](https://dev.springernature.com/))

---

## 🚀 Installation

### 1. Clone the repository

```bash
git clone https://github.com/mohamedsch/findPapers.git
cd findPapers
```

### 2. Build the project

Using Maven:

```bash
mvn clean package
```

After building, you will find the executable JAR in the target/ folder.

### ▶️ Usage

You can run the tool directly from the command line:

```bash
java -jar target/findPapers.jar -springerApi <API_KEY> -query "<your keywords>"
```

The results are stored in file named AllArticles.pdf and AllArticles-removeDup.pdf (after removing duplicated based on DOI).

### 🔑 Parameters

Parameter Description
-springerApi Your Springer API key (required).
-elsevierApi Your Elsevier API key (required).
-ieeeApi Your IEEE API key (required).
-pageSize page size of fetching default 20.
-query The keywords or search string for publications (required).

### 📌 Example Commands

1. Fetch articles with keyword "machine learning"

```bash

java -jar target/findPapers.jar -springerApi 6b3d8b5944d908e74b2f4aaf394518cc -elsevierApi b6af13427d79986a49b8c12105006181 -ieeeApi q8zt6u97bwdtbs8kbnqq8r2t -pageSize 20 -query "Machine Learning"
```

### 📊 Use in Systematic Mapping Study (SMS)

This tool was built to assist researchers performing systematic mapping studies by:

Automating the retrieval of relevant research papers.

Removal of duplicated papers from different APIs.

If you are conducting an SMS, you can:

Define your research questions and keywords.

Run multiple queries with this tool.

Store and analyze results.

### 📂 Project Structure

```bash
findPapers/
│── src/               # Source code
│── target/            # Compiled .jar after build
│── pom.xml            # Maven dependencies
│── README.md          # Documentation (this file)
📝 License
```

This project is licensed under the MIT License.
You are free to use, modify, and distribute this tool with proper attribution.

### 🤝 Contributing

Contributions are welcome!
If you want to improve or extend the tool:

Fork the repository

Create a new branch (feature/new-idea)

Commit your changes

Submit a pull request

---
