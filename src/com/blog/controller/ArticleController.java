package com.blog.controller;

import com.blog.domain.*;
import com.blog.utils.ParaUtils;
import com.blog.utils.RenderUtils;
import com.jfinal.core.Controller;
import com.jfinal.kit.Prop;
import com.jfinal.kit.PropKit;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.IAtom;
import com.jfinal.plugin.activerecord.Page;

import java.sql.SQLException;
import java.util.*;

/**
 * Created by qulongjun on 2017/7/15.
 */
public class ArticleController extends Controller {
    public void index() {
        renderText("aaa");
    }

    /**
     * 文章列表
     */
    public void list() {
        try {
            int rowCount = getParaToInt("rowCount");
            int currentPage = getParaToInt("currentPage");
            String condition_temp = getPara("condition");
            Map condition = ParaUtils.getSplitCondition(condition_temp);
            if (rowCount == 0) {
                rowCount = ParaUtils.getRowCount();
            }
            String paras = "WHERE a.state=1 ";
            String tables = " FROM `db_article` a ";
            Object[] keys = condition.keySet().toArray();
            for (int i = 0; i < keys.length; i++) {
                String key = (String) keys[i];
                Object value = condition.get(key);
                switch (key) {
                    case "category":
                        tables += ",`db_article_category` c ";
                        paras += " AND c.article_id = a.id AND c.category_id=" + value + " ";
                        break;
                    case "tag":
                        tables += ",`db_article_tag` t ";
                        paras += " AND t.article_id=a.id AND t.tag_id=" + value + " ";
                        break;
                    case "key":
                        paras += " AND (a.title like \"%" + value + "%\" OR a.summary like \"%" + value + "%\") ";
                        break;
                    case "time":
                        paras += " AND date_format(a.create_time,'%Y-%m')='" + value + "' ";
                        break;
                    default:
                        paras += ("AND " + key + " like \"%" + value + "%\"");
                }
            }
            Page<Article> articlePage = Article.articleDao.paginate(currentPage, rowCount, "SELECT a.*", tables + paras);
            List<Article> articleList = articlePage.getList();
            Map results = new HashMap();
            results.put("results", Article._toListJson(articleList, true));
            results.put("currentPage", currentPage);
            results.put("totalPage", articlePage.getTotalPage());
            results.put("rowCount", rowCount);
            results.put("condition", condition_temp);
            renderJson(results);
        } catch (Exception e) {
            renderError(500);
        }
    }


    /**
     * 根据id查找文章
     */
    public void findById() {
        try {
            int article_id = getParaToInt("id");
            Article article = Article.articleDao.findById(article_id);
            if (article != null) {
                Map result = article._toJson(false);
                renderJson(result);
            } else
                renderJson(RenderUtils.CODE_EMPTY);
        } catch (Exception e) {
            renderError(500);
        }
    }

    /**
     * 创建一篇博文
     */
    public void create() {
        try {
            Boolean result = Db.tx(new IAtom() {
                @Override
                public boolean run() throws SQLException {
                    Prop author = PropKit.use("author.properties");
                    Boolean result = true;
                    Article article = new Article();
                    article
                            .set("type", getPara("type"))
                            .set("title", getPara("title"))
                            .set("summary", getPara("summary"))
                            .set("create_time", ParaUtils.timeFormat.format(new Date()))
                            .set("author", author.get("name"))
                            .set("quote_author", getPara("quote_author"))
                            .set("content", getPara("content"))
                            .set("state", 1);
                    result = result && article.save();
                    Integer[] tagList = getParaValuesToInt("tags");
                    for (int tag : tagList) {
                        Article_Tag articleTag = new Article_Tag();
                        result = result && articleTag
                                .set("article_id", article.get("id"))
                                .set("tag_id", tag)
                                .save();
                    }
                    int category_id = getParaToInt("category");
                    Article_Category articleCategory = new Article_Category();
                    result = result && articleCategory
                            .set("article_id", article.get("id"))
                            .set("category_id", category_id)
                            .save();

                    String[] materials = getParaValues("materials");
                    for (String materialStr : materials) {
                        Material material = new Material();
                        result = result && material
                                .set("article_id", article.get("id"))
                                .set("material", material)
                                .save();
                    }
                    return result;
                }
            });
            renderJson(result ? RenderUtils.CODE_SUCCESS : RenderUtils.CODE_ERROR);
        } catch (Exception e) {
            renderError(500);
        }
    }


    public void hot() {
        try {
            List result = new ArrayList();
            List<Article> articleList = Article.articleDao.find("SELECT a.* FROM `db_comment` c,`db_article` a WHERE c.article_id=a.id AND a.type=2 AND a.state=1 GROUP BY c.article_id ORDER BY COUNT(*) DESC LIMIT 5\n");
            for (Article article : articleList) {
                Map temp = article._toJson(true);
                temp.put("comment_num", Comment.commentDao.find("SELECT * FROM `db_comment` c WHERE c.state=1 AND c.article_id=" + article.get("id")).size());
                result.add(temp);
            }
            Map t = new HashMap();
            t.put("results", result);
            renderJson(t);
        } catch (Exception e) {
            renderError(500);
        }

    }

    public void recently() {
        try {
            List<Article> articleList = Article._toListJson(Article.articleDao.find("SELECT * FROM `db_article` a WHERE a.state=1 AND a.type=2  ORDER BY create_time DESC LIMIT 0,5"), true);
            Map results = new HashMap();
            results.put("results", articleList);
            renderJson(results);
        } catch (Exception e) {
            renderError(500);
        }
    }
}
