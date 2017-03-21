
package com.chenlb.mmseg4j;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.io.PathUtils;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.plugin.analysis.mmseg.AnalysisMMsegPlugin;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Phonetic {

	private static final Logger log = ESLoggerFactory.getLogger(Phonetic.class.getName());

	private File dicPath;	//词库目录
	private volatile  Map<String, List<Explanation>> dict;

	private static final ConcurrentHashMap<File, Phonetic> dics = new ConcurrentHashMap<File, Phonetic>();

    /**
     * 词典的目录
     */
    private Phonetic(File path) {
        init(path);
    }

	protected void finalize() throws Throwable {
		/*
		 * 使 class reload 的时也可以释放词库
		 */
		destroy();
	}

	/**
	 * 从默认目录加载词库文件.<p/>
	 * 查找默认目录顺序:
	 * <ol>
	 * <li>从系统属性mmseg.dic.path指定的目录中加载</li>
	 * <li>从classpath/data目录</li>
	 * <li>从user.dir/data目录</li>
	 * </ol>
	 */
	public static Phonetic getInstance() {
		Path path = PathUtils.get(getDictRoot(), "mmseg");
		return getInstance(path.toFile());
	}

	/**
	 * @param path 词典的目录
	 */
	public static Phonetic getInstance(String path) {
		return getInstance(new File(path));
	}

	/**
	 * @param path 词典的目录
	 */
	public static Phonetic getInstance(File path) {
		File normalizeDir = normalizeFile(path);
		Phonetic dic = dics.get(normalizeDir);
		if(dic == null) {
			dic = new Phonetic(normalizeDir);
			dics.put(normalizeDir, dic);
		}
		return dic;
	}

	public static File normalizeFile(File file) {
		try {
			return file.getCanonicalFile();
		} catch (IOException e) {
			throw new RuntimeException("normalize file=["+file+"] fail", e);
		}
	}

	/**
	 * 销毁, 释放资源. 此后此对像不再可用.
	 */
	void destroy() {
		clear(dicPath);

		dicPath = null;
	}

	/**
	 * @see Phonetic#clear(File)
	 */
	public static Phonetic clear(String path) {
		return clear(new File(path));
	}

	/**
	 * 从单例缓存中去除
	 * @param path
	 * @return 没有返回 null
	 */
	public static Phonetic clear(File path) {
		File normalizeDir = normalizeFile(path);
		return dics.remove(normalizeDir);
	}

	private void init(File path) {
		dicPath = path;
		reload();	//加载词典
	}

	private Map<String, List<Explanation>> loadDic(File wordsPath) throws IOException {
		log.warn("load dict " + wordsPath.getCanonicalPath());
		InputStream charsIn;
		File charsFile = new File(wordsPath, "phonetic.dict");
		if(charsFile.exists()) {
			charsIn = new FileInputStream(charsFile);
		} else {	//从 jar 里加载
			charsIn = this.getClass().getResourceAsStream("/data/phonetic.dict");
		}
		Map<String, List<Explanation>> mapping = new HashMap<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(charsIn));

		String line;
		while((line = reader.readLine()) != null) {
			String[] parts = line.split("\\|");
			if (parts.length != 4) {
				continue;
			}
			List<Explanation> explanations = mapping.get(parts[0]);
			if (explanations == null) {
				explanations = new ArrayList<>();
			}
			explanations.add(new Explanation(parts[1], parts[2], parts[3]));
			mapping.put(parts[0], explanations);
		}
		log.warn("load dict complete");
		return mapping;
	}


	/**
	 * 全新加载词库，没有成功加载会回滚。<P/>
	 * 注意：重新加载时，务必有两倍的词库树结构的内存，默认词库是 50M/个 左右。否则抛出 OOM。
	 * @return 是否成
	 */
	public synchronized boolean reload() {
		Map<String, List<com.chenlb.mmseg4j.Explanation>> oldDict = dict;
		try {
			dict = loadDic(dicPath);
			log.info("load dic success");
		} catch (IOException e) {
			dict = oldDict;
            log.warn("reload dic error! dic="+dicPath+", and rollbacked.", e);
			return false;
		}
		return true;
	}

	public String match(Word word) {
		//log.info("search word: " + word.getString());

		List<Explanation> explanations = dict.get(word.getString());
		if (explanations == null) {
			//log.info("search word not found");
			return null;
		}
		return explanations.get(0).mBopomofo;
	}

	public static String getDictRoot() {
		return PathUtils.get(
				new File(AnalysisMMsegPlugin.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent(), "config")
				.toAbsolutePath().toString();
	}
}
