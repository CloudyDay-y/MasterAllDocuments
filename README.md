# MasterIndex - Java文档搜索引擎

## 功能特性

-  **倒排索引**：经典的信息检索数据结构，快速关键词查找（支持检索图片内容）
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

JDK版本1.8

在你的项目的`pom.xml`中添加以下依赖：

```xml
<dependency>
    <groupId>io.github.cloudyday-y</groupId>
    <artifactId>master-all-documents</artifactId>
    <version>1.0.1</version>
</dependency>

//如果需要ocr支持，还需要添加：
<dependency>
    <groupId>io.github.mymonstercat</groupId>
    <artifactId>rapidocr</artifactId>
    <version>0.0.7</version>
</dependency>
<dependency>
    <groupId>io.github.mymonstercat</groupId>
    <artifactId>rapidocr-onnx-platform</artifactId>
    <version>0.0.7</version>
</dependency>
```
```angular2html
1.对于java项目，将本项目根目录下的(master_index_config.json)文件放在您项目的根目录下

2.对于spring项目，创建一个配置类：用以控制ocr是否开启等参数

@Configuration
public class MasterIndexConfig {

        @Bean
        public AppConfig appConfig() {
            AppConfig config = AppConfig.builder()
                .withOcr(ocr -> {
                ocr.setEnabled(true);
                ocr.setTimeout(30000);
            })
            .withIndexing(indexing -> {
                indexing.setEnableOcr(true);
                indexing.setMaxFileSizeMb(50);
            })
           .build();
        
        AppConfig.init(config);
        
        return config;
        }
}
```

```bash
mvn clean package
```

### 2. 编程方式使用

#### 基本用法

```java

// 1. 构建索引 设置索引所在路径
LuceneIndexBuilder builder = new LuceneIndexBuilder("./my_index");
//将目录下所有支持的文件添加到索引
builder.addDirectory("/path/to/documents");
//添加单个文件到索引
builder.addDocument("/path/to/new_file.txt");
builder.save();

// 2. 搜索，指定索引路径
LuceneSearcher searcher = new LuceneSearcher("./my_index");
List<SearchResult> results = searcher.search("java", 10);

注：支持使用自己的分词器，但是添加索引和搜索时需要保持一致

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
LuceneIndexBuilder searcher = new LuceneIndexBuilder("./my_index");

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


### 3. OCR支持

OCR使用的PaddleOCR, 运行在java用的https://github.com/MyMonsterCat/RapidOcr-Java?tab=readme-ov-file

不同操作系统需要引入不同依赖具体可查看他的文档，在windows编译是无法直接用于其他平台的，需要修改发布平台的maven依赖编译
比如本设置是macos-arm64，我想发布到linux-x86_64平台，则需要修改为linux-x86_64的ocr依赖进行编译发布
将
```
<dependency>
    <groupId>io.github.mymonstercat</groupId>
    <artifactId>rapidocr-onnx-platform</artifactId>
    <version>0.0.7</version>
</dependency>
```
改为linux-x86_64的版本编译后部署到linux-x86_64平台即可

当前使用的是CPU版本 若需要GPU可查看对应文档编译
