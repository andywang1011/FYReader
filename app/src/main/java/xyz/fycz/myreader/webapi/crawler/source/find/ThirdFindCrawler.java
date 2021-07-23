package xyz.fycz.myreader.webapi.crawler.source.find;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

import javax.script.SimpleBindings;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import xyz.fycz.myreader.entity.FindKind;
import xyz.fycz.myreader.entity.StrResponse;
import xyz.fycz.myreader.greendao.entity.Book;
import xyz.fycz.myreader.greendao.entity.rule.BookSource;
import xyz.fycz.myreader.greendao.entity.rule.FindRule;
import xyz.fycz.myreader.model.sourceAnalyzer.BookSourceManager;
import xyz.fycz.myreader.model.third2.analyzeRule.AnalyzeRule;
import xyz.fycz.myreader.util.utils.StringUtils;
import xyz.fycz.myreader.webapi.crawler.base.BaseFindCrawler;

import static xyz.fycz.myreader.common.APPCONST.SCRIPT_ENGINE;

/**
 * @author fengyue
 * @date 2021/7/22 22:30
 */
public class ThirdFindCrawler extends BaseFindCrawler {
    private BookSource source;
    private FindRule findRuleBean;
    private AnalyzeRule analyzeRule;
    private String findError = "发现规则语法错误";

    public ThirdFindCrawler(BookSource source) {
        this.source = source;
        findRuleBean = source.getFindRule();
    }

    public BookSource getSource() {
        return source;
    }

    @Override
    public String getName() {
        return source.getSourceName();
    }

    @Override
    public String getTag() {
        return source.getSourceUrl();
    }

    @Override
    public Observable<Boolean> initData() {
        return Observable.create(emitter -> {
            try {
                String[] kindA;
                String findRule;
                if (!TextUtils.isEmpty(findRuleBean.getUrl()) && !source.containsGroup(findError)) {
                    boolean isJs = findRuleBean.getUrl().startsWith("<js>");
                    if (isJs) {
                        String jsStr = findRuleBean.getUrl().substring(4, findRuleBean.getUrl().lastIndexOf("<"));
                        findRule = evalJS(jsStr, source.getSourceUrl()).toString();
                    } else {
                        findRule = findRuleBean.getUrl();
                    }
                    kindA = findRule.split("(&&|\n)+");
                    List<FindKind> children = new ArrayList<>();
                    String groupName = getName();
                    for (String kindB : kindA) {
                        if (kindB.trim().isEmpty()) continue;
                        String[] kind = kindB.split("::");
                        if (kind.length == 1){
                            if (children.size() > 0) {
                                kindsMap.put(groupName, children);
                                children = new ArrayList<>();
                            }
                            groupName = kind[0].replaceAll("\\s", "");
                            continue;
                        }
                        FindKind findKindBean = new FindKind();
                        findKindBean.setTag(source.getSourceUrl());
                        findKindBean.setName(kind[0]);
                        findKindBean.setUrl(kind[1]);
                        children.add(findKindBean);
                    }
                    kindsMap.put(groupName, children);
                }
                emitter.onNext(true);
            } catch (Exception exception) {
                source.addGroup(findError);
                BookSourceManager.addBookSource(source);
                emitter.onNext(false);
            }
            emitter.onComplete();
        });
    }

    @Override
    public Observable<List<Book>> getFindBooks(StrResponse strResponse, FindKind kind) {
        return null;
    }

    /**
     * 执行JS
     */
    private Object evalJS(String jsStr, String baseUrl) throws Exception {
        SimpleBindings bindings = new SimpleBindings();
        bindings.put("java", getAnalyzeRule());
        bindings.put("baseUrl", baseUrl);
        return SCRIPT_ENGINE.eval(jsStr, bindings);
    }

    private AnalyzeRule getAnalyzeRule() {
        if (analyzeRule == null) {
            analyzeRule = new AnalyzeRule(null);
        }
        return analyzeRule;
    }
}
