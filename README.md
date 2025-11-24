# MasterIndex - Java文档搜索引擎

## 功能特性

-  **倒排索引**：经典的信息检索数据结构，快速关键词查找（支持检索图片内容）,后续会上传到maven中央仓库
-  **多格式支持**：
  - 文本文件：.txt, .md, .java, .py, .js, .cpp, .html, .json, .xml, .yaml, .sql 等
  - Office文档：.docx (Word), .xlsx (Excel), .pptx (PowerPoint)
  - PDF文档：.pdf
  - 图片文件：.png, .jpg, .jpeg, .bmp
-  **中英文支持**：
  - 中文分词（使用HanLP）
  - 英文单词提取
-  **增量索引**：仅重新索引已更改的文件
-  **多种搜索模式**：
  - 关键词搜索（TF评分）
  - 短语搜索（精确匹配）
  - 文件类型过滤
  - 扩展名过滤

## 快速开始

### 1. 编译项目

```bash
cd MasterAllDocuments
mvn clean package
```

### 2. 编程方式使用

#### 基本用法

```java

// 1. 构建索引
LuceneIndexBuilder builder = new LuceneIndexBuilder("./my_index");
builder.addDirectory("/path/to/documents");
builder.addDocument("/path/to/new_file.txt");
builder.save();

// 2. 搜索
LuceneSearcher searcher = new LuceneSearcher("./my_index");
List<SearchResult> results = searcher.search("java", 10);


```

#### 高级用法

```java

LuceneIndexBuilder builder = new LuceneIndexBuilder("./my_index");

// 1. 只索引特定文件类型
Set<String> extensions = new HashSet<>(Arrays.asList(".txt", ".md", ".java"));
builder.addDirectory("/path/to/docs", true, false, extensions);

// 2. 增量更新（添加单个文档）,第二个参数force表示是否重新索引，无论是否已经索引过，false表示仅索引未索引或已更改的文件
builder.addDocument("/path/to/new_file.txt", false);
builder.save();

// 3. 搜索
Searcher searcher = new Searcher("./my_index");

// 关键词搜索
List<SearchResult> results = searcher.search("algorithm", 10);

// 短语搜索（精确匹配）
List<SearchResult> phraseResults = searcher.searchPhrase("binary search tree", 5);

// 按文件类型过滤
List<SearchResult> textOnly = searcher.search("java", 10, "text");

// 按扩展名搜索
List<SearchResult> javaFiles = searcher.searchByExtension(".java", 20);

// 获取统计信息
Map<String, Object> stats = searcher.getStats();
System.out.println("Total documents: " + stats.get("total_documents"));
System.out.println("Total words: " + stats.get("total_words"));
```

