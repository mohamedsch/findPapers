# FindPapers

This project is a **Command Line Interface (CLI) tool** developed in **Java** for interacting with the **Springer, ELSEVIER, IEEE, Web of Science, and CrossRef APIs**.  
It is part of a **Systematic Mapping Study (SMS)**.

---

## 📖 Features

- Fetch publications from the **Springer, ELSEVIER, IEEE, Web of Science, and CrossRef APIs**.
- Filter results based on Queries, keywords, dates, or publication type.
- Export data for further analysis in XLSX/PDF format.
- Designed for **Systematic Mapping Studies**.

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

### 3. Results

- AllArticles.xlsx represents all papers fetched.
- py.py Python script to remove duplications and result in clean_output.xlsx file.
- Selected papers in Selected.xlsx

