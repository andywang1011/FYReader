package xyz.fycz.myreader.ui.bookinfo;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import xyz.fycz.myreader.R;
import xyz.fycz.myreader.base.BaseActivity2;
import xyz.fycz.myreader.callback.ResultCallback;
import xyz.fycz.myreader.common.APPCONST;
import xyz.fycz.myreader.crawler.BookInfoCrawler;
import xyz.fycz.myreader.crawler.ReadCrawler;
import xyz.fycz.myreader.crawler.ReadCrawlerUtil;
import xyz.fycz.myreader.creator.ChangeSourceDialog;
import xyz.fycz.myreader.creator.DialogCreator;
import xyz.fycz.myreader.enums.BookSource;
import xyz.fycz.myreader.greendao.entity.Book;
import xyz.fycz.myreader.greendao.service.BookService;
import xyz.fycz.myreader.ui.read.ReadActivity;
import xyz.fycz.myreader.util.StringHelper;
import xyz.fycz.myreader.util.TextHelper;
import xyz.fycz.myreader.util.utils.NetworkUtils;
import xyz.fycz.myreader.webapi.CommonApi;

import java.util.ArrayList;

/**
 * @author fengyue
 * @date 2020/8/17 11:39
 */
public class BookDetailedActivity extends BaseActivity2 {
    @BindView(R.id.book_detail_iv_cover)
    ImageView mIvCover;
    @BindView(R.id.book_detail_tv_author)
    TextView mTvAuthor;
    @BindView(R.id.book_detail_tv_type)
    TextView mTvType;
    @BindView(R.id.book_detail_newest_chapter)
    TextView mTvNewestChapter;
    @BindView(R.id.book_detail_source)
    TextView mTvSource;
    @BindView(R.id.book_detail_tv_add)
    TextView bookDetailTvAdd;
    @BindView(R.id.book_detail_tv_open)
    TextView bookDetailTvOpen;
    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.book_detail_tv_desc)
    TextView mTvDesc;
    @BindView(R.id.tv_disclaimer)
    TextView mTvDisclaimer;
    @BindView(R.id.fl_add_bookcase)
    FrameLayout flAddBookcase;
    @BindView(R.id.fl_open_book)
    FrameLayout flOpenBook;
    @BindView(R.id.book_detail_rv_catalog)
    RecyclerView bookDetailRvCatalog;
    @BindView(R.id.pb_loading)
    ProgressBar pbLoading;


    private Book mBook;
    private ArrayList<Book> aBooks;
    private BookService mBookService;
    private ReadCrawler mReadCrawler;
    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    initBookInfo();
                    break;
                case 2:
                    createChangeSourceDia();
                    break;
                case 3:
                    pbLoading.setVisibility(View.GONE);
                    DialogCreator.createTipDialog(BookDetailedActivity.this, "未搜索到该书籍，书源加载失败！");
                    break;
                case 4:
                    initOtherInfo();
                    break;
            }
        }
    };

    @Override
    protected int getContentId() {
        return R.layout.activity_book_detail;
    }

    @Override
    protected void initData(Bundle savedInstanceState) {
        super.initData(savedInstanceState);
        mBookService = BookService.getInstance();
        aBooks = (ArrayList<Book>) getIntent().getSerializableExtra(APPCONST.SEARCH_BOOK_BEAN);
        if (aBooks != null) {
            mBook = aBooks.get(0);
        } else {
            mBook = (Book) getIntent().getSerializableExtra(APPCONST.BOOK);
        }
        mReadCrawler = ReadCrawlerUtil.getReadCrawler(mBook.getSource());
    }

    @Override
    protected void setUpToolbar(Toolbar toolbar) {
        super.setUpToolbar(toolbar);
        getSupportActionBar().setTitle(mBook.getName());
    }

    @Override
    protected void initWidget() {
        super.initWidget();
        initBookInfo();
        mTvDisclaimer.setOnClickListener(v -> DialogCreator.createAssetTipDialog(this, "免责声明", "disclaimer.fy"));
        if (isBookCollected()) {
            bookDetailTvAdd.setText("移除书籍");
            bookDetailTvOpen.setText("继续阅读");
        }

    }

    @Override
    protected void initClick() {
        super.initClick();
        flAddBookcase.setOnClickListener(view -> {
            if (!isBookCollected()) {
                mBookService.addBook(mBook);
                TextHelper.showText("成功加入书架");
                bookDetailTvAdd.setText("移除书籍");
            } else {
                mBookService.deleteBookById(mBook.getId());
                TextHelper.showText("成功移除书籍");
                bookDetailTvAdd.setText("加入书架");
                bookDetailTvOpen.setText("开始阅读");
            }
        });
        flOpenBook.setOnClickListener(view -> {
            final boolean isCollected;
            if (isBookCollected()) {
                isCollected = true;
            } else {
                mBookService.addBook(mBook);
                isCollected = false;
                CommonApi.getBookChapters(mBook.getChapterUrl(), mReadCrawler, new ResultCallback() {
                    @Override
                    public void onFinish(Object o, int code) {
                        mBookService.updateEntity(mBook);
                    }

                    @Override
                    public void onError(Exception e) {
                    }
                });
            }
            Intent intent = new Intent(this, ReadActivity.class);
            intent.putExtra(APPCONST.BOOK, mBook);
            intent.putExtra("isCollected", isCollected);
            startActivityForResult(intent, APPCONST.REQUEST_READ);
        });
    }

    @Override
    protected void processLogic() {
        super.processLogic();
    }

    private boolean isBookCollected() {
        Book book = mBookService.findBookByAuthorAndName(mBook.getName(), mBook.getAuthor());
        if (book == null) {
            return false;
        } else {
            mBook = book;
            return true;
        }
    }

    private void initBookInfo() {
        mTvAuthor.setText(mBook.getAuthor());
        if (StringHelper.isEmpty(mBook.getImgUrl())) {
            mBook.setImgUrl("");
        }
        assert mBook.getNewestChapterTitle() != null;
        mTvNewestChapter.setText("最新章节:" + mBook.getNewestChapterTitle().replace("最近更新 ", ""));
        mTvDesc.setText("");
        mTvType.setText("");
        if (!"null".equals(mBook.getSource())) {
            mTvSource.setText("书源：" + BookSource.fromString(mBook.getSource()).text);
        }
        ReadCrawler rc = ReadCrawlerUtil.getReadCrawler(mBook.getSource());
        if (rc instanceof BookInfoCrawler && StringHelper.isEmpty(mBook.getImgUrl())) {
            BookInfoCrawler bic = (BookInfoCrawler) rc;
            CommonApi.getBookInfo(mBook, bic, new ResultCallback() {
                @Override
                public void onFinish(Object o, int code) {
                    mHandler.sendMessage(mHandler.obtainMessage(4));
                }

                @Override
                public void onError(Exception e) {

                }
            });
        } else {
            initOtherInfo();
        }
    }

    private void initOtherInfo() {
        mTvDesc.setText(mBook.getDesc());
        mTvType.setText(mBook.getType());
        Glide.with(this)
                .load(mBook.getImgUrl())
                .error(R.mipmap.no_image)
                .placeholder(R.mipmap.no_image)
                //设置圆角
                .apply(RequestOptions.bitmapTransform(new RoundedCorners(8)))
                .into(mIvCover);
    }

    private void createChangeSourceDia() {
        if (aBooks == null){
            mHandler.sendMessage(mHandler.obtainMessage(3));
            return;
        }
        pbLoading.setVisibility(View.GONE);
        CharSequence[] sources = new CharSequence[aBooks.size()];
        int checkedItem = 0;
        for (int i = 0; i < sources.length; i++) {
            sources[i] = BookSource.fromString(aBooks.get(i).getSource()).text
                    + "\n" + aBooks.get(i).getNewestChapterTitle();
            if (sources[i].equals(BookSource.fromString(mBook.getSource()).text
                    + "\n" + aBooks.get(i).getNewestChapterTitle())) {
                checkedItem = i;
            }
        }
        final int finalCheckedItem = checkedItem;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("切换书源")
                .setCancelable(true)
                .setSingleChoiceItems(sources, checkedItem, (dialog1, which) -> {
                    boolean isBookCollected = isBookCollected();
                    if (finalCheckedItem == which) {
                        dialog1.dismiss();
                        return;
                    }
                    Book book = aBooks.get(which);
                    Book bookTem = new Book(mBook);
                    bookTem.setChapterUrl(book.getChapterUrl());
                    bookTem.setImgUrl(book.getImgUrl());
                    bookTem.setType(book.getType());
                    bookTem.setDesc(book.getDesc());
                    bookTem.setSource(book.getSource());
                    if (isBookCollected) {
                        mBookService.updateBook(mBook, bookTem);
                    }
                    mBook = bookTem;
                    mHandler.sendMessage(mHandler.obtainMessage(1));
                    if (isBookCollected) {
                        DialogCreator.createTipDialog(this,
                                "换源成功，由于不同书源的章节数量不一定相同，故换源后历史章节可能出错！");
                    }
                    dialog1.dismiss();
                }).create();
        dialog.show();
    }

    /********************************Event***************************************/
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_book_detail, menu);
        return true;
    }

    /**
     * 导航栏菜单点击事件
     *
     * @param item
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_change_source:  //换源
                if (!NetworkUtils.isNetWorkAvailable()){
                    TextHelper.showText("无网络连接！");
                    return true;
                }
                pbLoading.setVisibility(View.VISIBLE);
                if (aBooks == null) {
                    ChangeSourceDialog csd = new ChangeSourceDialog(this, mBook);
                    csd.init(new ResultCallback() {
                        @Override
                        public void onFinish(Object o, int code) {
                            aBooks = (ArrayList<Book>) o;
                            mHandler.sendMessage(mHandler.obtainMessage(2));
                        }

                        @Override
                        public void onError(Exception e) {
                            mHandler.sendMessage(mHandler.obtainMessage(3));
                        }
                    });
                } else {
                    createChangeSourceDia();
                }
                break;
            case R.id.action_reload:  //重新加载
                initWidget();
                processLogic();
                break;
            case R.id.action_open_link:  //打开链接
                Uri uri = Uri.parse(mBook.getChapterUrl());
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == APPCONST.REQUEST_READ) {
            if (data == null) {
                return;
            }
            boolean isCollected = data.getBooleanExtra(APPCONST.RESULT_IS_COLLECTED, false);
            if (isCollected) {
                bookDetailTvAdd.setText("移除书籍");
                bookDetailTvOpen.setText("继续阅读");
            }
        }
    }
}
