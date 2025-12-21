import pandas as pd
from itertools import combinations

# --------------------------------------------
# LOAD FILE
# --------------------------------------------
df = pd.read_excel("AllArticles.xlsx")

# Normalize DOI values
df["DOI"] = df["DOI"].astype(str).str.strip()
df["DOI"] = df["DOI"].replace({"nan": "", "None": ""})

# Split papers: with DOI / without DOI
with_doi = df[df["DOI"] != ""].copy()
no_doi = df[df["DOI"] == ""].copy()

original_count = len(with_doi)
with_no_doi = len(no_doi)
original=original_count+with_no_doi
print(f"Original papers: {original}")

# ========================================================
# 1️⃣ CREATE CLEAN OUTPUT (REMOVE DUPLICATE DOIs)
# ========================================================

# Group by DOI and merge sources
grouped = (
    with_doi.groupby("DOI").agg({
        "Title": "first",
        "Authors": "first",
        "Abstract": lambda x: next((a for a in x if pd.notna(a) and a != ""), ""),
        "Source": lambda x: "; ".join(sorted(set(x)))
    })
).reset_index()

cleaned_count = len(grouped)

# How many duplicates were removed
removed_count = original_count - cleaned_count
print(f"Papers removed (duplicates based on DOI): {removed_count}")

# Add papers with NO DOI
clean_df = pd.concat([grouped, no_doi], ignore_index=True)

# Save
clean_df.to_excel("clean_output.xlsx", index=False)
print("Saved: clean_output.xlsx")


# ========================================================
# 2️⃣ CREATE FULL SOURCE × SOURCE DUPLICATE MATRIX
# ========================================================

# Build DOI -> unique list of sources
doi_to_sources = (
    with_doi.groupby("DOI")["Source"]
    .apply(lambda x: sorted(set(x)))
)

# All unique sources
all_sources = sorted(df["Source"].unique())

# Initialize empty square matrix
matrix = pd.DataFrame(0, index=all_sources, columns=all_sources)

# Fill matrix: count overlaps
for sources in doi_to_sources:
    for s1 in sources:
        for s2 in sources:
            if s1 != s2:
                matrix.loc[s1, s2] += 1

# Save matrix
matrix.to_excel("doi_overlap_matrix.xlsx")

print("Saved: doi_overlap_matrix.xlsx")
print("\n=== Overlap Matrix ===")
print(matrix)
