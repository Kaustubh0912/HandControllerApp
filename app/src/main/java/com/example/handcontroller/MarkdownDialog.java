package com.example.handcontroller;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.graphics.Typeface;
import androidx.core.text.HtmlCompat;

public class MarkdownDialog extends Dialog {
    private String content;
    private String title;

    public MarkdownDialog(Context context, String title, String content) {
        super(context);
        this.title = title;
        this.content = content;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_markdown);

        TextView titleView = findViewById(R.id.dialogTitle);
        TextView contentView = findViewById(R.id.dialogContent);

        titleView.setText(title);

        // Convert markdown to formatted text
        String formattedContent = formatMarkdown(content);
        contentView.setText(HtmlCompat.fromHtml(formattedContent, HtmlCompat.FROM_HTML_MODE_LEGACY));

        findViewById(R.id.closeButton).setOnClickListener(v -> dismiss());Window window = getWindow();
        if (window != null) {
            window.setLayout(
                    (int)(getContext().getResources().getDisplayMetrics().widthPixels * 0.9),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

    }

    private String formatMarkdown(String markdown) {
        // Basic markdown to HTML conversion
        String html = markdown
                .replaceAll("## (.*?)\\n", "<h2>$1</h2>")
                .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>")
                .replaceAll("\\n\\s*-(.*?)\\n", "<br/>• $1<br/>")
                .replaceAll("\\n", "<br/>");

        return html;
    }
}