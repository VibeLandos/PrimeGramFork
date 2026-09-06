package org.telegram.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.PrimeVpnServerStore;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import vpn.sdk.VpnSDK;

/**
 * PrimeGram: a Happ-style list of VLESS/VMess/Trojan/Shadowsocks servers, sitting on top of
 * {@link VpnSDK} the way {@link PrimeIconPacksActivity} sits on top of {@link
 * org.telegram.messenger.PrimeIconPacks} - VpnSDK itself only ever remembers one active config at
 * a time ({@code setCustomVlessConfig}), it has no notion of a list; {@link PrimeVpnServerStore}
 * is what supplies that, and this screen is what lets a person paste keys the way a proxy client
 * does instead of juggling links in a notes app.
 */
public class PrimeVpnServersActivity extends UniversalFragment {

    private static final int ID_ADD = 1;
    private static final int ID_PASTE = 2;
    private static final int ID_FREE_KEY = 3;
    private static final int ID_PING_ALL = 4;
    private static final int ID_PING_TCP = 5;
    private static final int ID_PING_TLS = 6;
    private static final int ID_PING_HTTP = 7;
    private static final int ID_SERVER_BASE = 100;

    private static final int PING_TIMEOUT_TCP_MS = 3000;
    private static final int PING_TIMEOUT_HTTP_MS = 8000;

    private List<PrimeVpnServerStore.Server> servers = new ArrayList<>();
    private boolean pinging;

    @Override
    public boolean onFragmentCreate() {
        reload();
        return super.onFragmentCreate();
    }

    private void reload() {
        servers = PrimeVpnServerStore.getServers();
    }

    @Override
    protected CharSequence getTitle() {
        return "Серверы";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final String activeId = PrimeVpnServerStore.getActiveServerId();
        final boolean autoActive = activeId == null && VpnSDK.hasCachedXrayConfig() && VpnSDK.isProxyRunning();
        final String clip = readClipboard();
        final int clipLinks = PrimeVpnServerStore.extractShareLinks(clip).size();
        final boolean clipSub = PrimeVpnServerStore.looksLikeSubscriptionUrl(clip.trim());

        items.add(UItem.asButton(ID_ADD, "Добавить ключи", "vless / vmess / trojan / ss / socks / подписка"));
        if (clipLinks > 0 || clipSub) {
            items.add(UItem.asButton(ID_PASTE, "Вставить из буфера",
                    clipSub ? "ссылка подписки" : (clipLinks + " " + (clipLinks == 1 ? "ключ" : "ключей"))));
        }
        items.add(UItem.asButton(ID_FREE_KEY, "Попробовать бесплатный ключ", freeKeySubtitle()));
        items.add(UItem.asShadow("Можно вставить пачку ссылок сразу, как в Happ или v2rayNG. Имя берётся из #remark. "
                + "Бесплатный ключ не хранится отдельной строкой."));

        if (!servers.isEmpty()) {
            items.add(UItem.asHeader("Проверка"));
            final int mode = PrimeVpnServerStore.getPingMode();
            items.add(UItem.asRadio(ID_PING_TCP, "TCP", "порт открыт").setChecked(mode == VpnSDK.DELAY_TCP));
            items.add(UItem.asRadio(ID_PING_TLS, "TLS", "хендшейк").setChecked(mode == VpnSDK.DELAY_TLS));
            items.add(UItem.asRadio(ID_PING_HTTP, "HTTP GET", "трафик через узел").setChecked(mode == VpnSDK.DELAY_HTTP));
            items.add(UItem.asButton(ID_PING_ALL, pinging ? "Проверяем..." : "Проверить все", pingModeHint(mode)));
            items.add(UItem.asShadow(mode == VpnSDK.DELAY_HTTP
                    ? "HTTP GET идёт через сам ключ (generate_204). Пока проверяем, текущий VLESS на секунды отключается, потом вернётся."
                    : "TCP/TLS не трогают текущее подключение и бегут параллельно."));

            items.add(UItem.asHeader("Мои серверы"));
            for (int i = 0; i < servers.size(); i++) {
                final PrimeVpnServerStore.Server s = servers.get(i);
                final String subtitle = s.protocol.toUpperCase(java.util.Locale.ROOT)
                        + pingLabel(s.lastPingMs);
                items.add(UItem.asCheck(ID_SERVER_BASE + i, s.name + "\n" + subtitle)
                        .setChecked(!autoActive && s.id.equals(activeId)));
            }
            items.add(UItem.asShadow("Тап — подключиться. Долгий тап — копировать, проверить или удалить."));
        }
    }

    private static String pingModeHint(int mode) {
        if (mode == VpnSDK.DELAY_TLS) {
            return "TLS handshake";
        }
        if (mode == VpnSDK.DELAY_HTTP) {
            return "GET generate_204 через ключ";
        }
        return "TCP connect";
    }

    private static String pingLabel(long ms) {
        if (ms >= 0) {
            return " · " + ms + " мс";
        }
        if (ms == -2) {
            return " · недоступен";
        }
        return "";
    }

    private String freeKeySubtitle() {
        final String activeId = PrimeVpnServerStore.getActiveServerId();
        if (activeId != null) {
            return "выключен, активен свой сервер";
        }
        if (VpnSDK.isProxyRunning()) {
            return "активен";
        }
        return VpnSDK.hasCachedXrayConfig() ? "ключ есть, прокси выключен" : "ещё не запрашивался";
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ADD) {
            showAddServerDialog(readClipboardIfShareOrSub());
        } else if (item.id == ID_PASTE) {
            importFromClipboard();
        } else if (item.id == ID_FREE_KEY) {
            requestFreeKey();
        } else if (item.id == ID_PING_ALL) {
            pingAll();
        } else if (item.id == ID_PING_TCP) {
            PrimeVpnServerStore.setPingMode(VpnSDK.DELAY_TCP);
            listView.adapter.update(true);
        } else if (item.id == ID_PING_TLS) {
            PrimeVpnServerStore.setPingMode(VpnSDK.DELAY_TLS);
            listView.adapter.update(true);
        } else if (item.id == ID_PING_HTTP) {
            PrimeVpnServerStore.setPingMode(VpnSDK.DELAY_HTTP);
            listView.adapter.update(true);
        } else if (item.id >= ID_SERVER_BASE && item.id < ID_SERVER_BASE + servers.size()) {
            connectTo(servers.get(item.id - ID_SERVER_BASE));
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= ID_SERVER_BASE && item.id < ID_SERVER_BASE + servers.size()) {
            showServerMenu(servers.get(item.id - ID_SERVER_BASE));
            return true;
        }
        return false;
    }

    private void showServerMenu(PrimeVpnServerStore.Server server) {
        if (getParentActivity() == null) {
            return;
        }
        new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(server.name)
                .setItems(new CharSequence[]{"Копировать ключ", "Проверить", "Удалить"}, (dialog, which) -> {
                    if (which == 0) {
                        AndroidUtilities.addToClipboard(server.rawUrl);
                        Toast.makeText(getContext(), "Скопировано", Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        pingOneServer(server);
                    } else if (which == 2) {
                        confirmDelete(server);
                    }
                })
                .show();
    }

    private void pingAll() {
        if (pinging || servers.isEmpty()) {
            return;
        }
        pinging = true;
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
        final List<PrimeVpnServerStore.Server> snapshot = new ArrayList<>(servers);
        final int mode = PrimeVpnServerStore.getPingMode();
        final int timeout = mode == VpnSDK.DELAY_HTTP ? PING_TIMEOUT_HTTP_MS : PING_TIMEOUT_TCP_MS;
        Utilities.globalQueue.postRunnable(() -> {
            if (mode == VpnSDK.DELAY_HTTP) {
                final String[] urls = new String[snapshot.size()];
                for (int i = 0; i < snapshot.size(); i++) {
                    urls[i] = snapshot.get(i).rawUrl;
                }
                final long[] results = VpnSDK.measureShareLinkDelays(urls, mode, timeout);
                for (int i = 0; i < snapshot.size(); i++) {
                    snapshot.get(i).lastPingMs = results[i];
                    PrimeVpnServerStore.updatePing(snapshot.get(i).id, results[i]);
                }
            } else {
                final java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(
                        Math.min(8, snapshot.size()));
                final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(snapshot.size());
                for (PrimeVpnServerStore.Server server : snapshot) {
                    pool.submit(() -> {
                        try {
                            server.lastPingMs = VpnSDK.measureShareLinkDelay(server.rawUrl, mode, timeout);
                            PrimeVpnServerStore.updatePing(server.id, server.lastPingMs);
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                try {
                    latch.await();
                } catch (InterruptedException ignored) {
                }
                pool.shutdown();
            }
            AndroidUtilities.runOnUIThread(() -> {
                pinging = false;
                reload();
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
        });
    }

    private void pingOneServer(PrimeVpnServerStore.Server server) {
        if (pinging) {
            return;
        }
        pinging = true;
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
        final int mode = PrimeVpnServerStore.getPingMode();
        final int timeout = mode == VpnSDK.DELAY_HTTP ? PING_TIMEOUT_HTTP_MS : PING_TIMEOUT_TCP_MS;
        Utilities.globalQueue.postRunnable(() -> {
            final long ms = VpnSDK.measureShareLinkDelay(server.rawUrl, mode, timeout);
            PrimeVpnServerStore.updatePing(server.id, ms);
            AndroidUtilities.runOnUIThread(() -> {
                pinging = false;
                reload();
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
                if (getContext() != null) {
                    Toast.makeText(getContext(),
                            ms >= 0 ? (server.name + ": " + ms + " мс") : (server.name + ": недоступен"),
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void requestFreeKey() {
        if (getParentActivity() == null) {
            return;
        }
        PrimeVpnServerStore.setActiveServerId(null);
        Toast.makeText(getContext(), "Получаем ключ...", Toast.LENGTH_SHORT).show();
        VpnSDK.registerOrAuth(2, success -> {
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            if (!success) {
                Toast.makeText(getContext(), "Не удалось получить ключ. Проверьте соединение и попробуйте ещё раз.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void connectTo(PrimeVpnServerStore.Server server) {
        if (getParentActivity() == null) {
            return;
        }
        Toast.makeText(getContext(), "Подключаемся...", Toast.LENGTH_SHORT).show();
        Utilities.globalQueue.postRunnable(() -> {
            final boolean ok = VpnSDK.setCustomVlessConfig(server.rawUrl);
            AndroidUtilities.runOnUIThread(() -> {
                if (ok) {
                    PrimeVpnServerStore.setActiveServerId(server.id);
                    Toast.makeText(getContext(), "«" + server.name + "» подключён", Toast.LENGTH_SHORT).show();
                } else {
                    final String reason = VpnSDK.getLastCustomVlessError();
                    new AlertDialog.Builder(getParentActivity())
                            .setTitle("Не удалось подключиться")
                            .setMessage("«" + server.name + "» не отвечает" + (reason != null ? " (" + reason + ")" : "") + ".")
                            .setPositiveButton(LocaleController.getString(R.string.OK), null)
                            .show();
                }
                reload();
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            });
        });
    }

    private void confirmDelete(PrimeVpnServerStore.Server server) {
        if (getParentActivity() == null) {
            return;
        }
        new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle("Удалить «" + server.name + "»?")
                .setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
                    final boolean wasActive = server.id.equals(PrimeVpnServerStore.getActiveServerId());
                    PrimeVpnServerStore.removeServer(server.id);
                    if (wasActive) {
                        VpnSDK.stopProxy();
                    }
                    reload();
                    listView.adapter.update(true);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void importFromClipboard() {
        final String clip = readClipboard();
        if (PrimeVpnServerStore.looksLikeSubscriptionUrl(clip.trim())) {
            importSubscription(clip.trim(), null);
            return;
        }
        final int added = PrimeVpnServerStore.addServersFromText(null, clip);
        afterImport(added, clip);
    }

    private void afterImport(int added, String sourceText) {
        reload();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
        if (getContext() == null) {
            return;
        }
        if (added > 0) {
            Toast.makeText(getContext(), "Добавлено: " + added, Toast.LENGTH_SHORT).show();
        } else if (!PrimeVpnServerStore.extractShareLinks(sourceText).isEmpty()) {
            Toast.makeText(getContext(), "Эти ключи уже есть в списке", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "Не нашли vless/vmess/trojan/ss/socks ссылок", Toast.LENGTH_LONG).show();
        }
    }

    private void importSubscription(String url, String nameHint) {
        if (getParentActivity() == null) {
            return;
        }
        Toast.makeText(getContext(), "Загружаем подписку...", Toast.LENGTH_SHORT).show();
        Utilities.globalQueue.postRunnable(() -> {
            String body = null;
            String error = null;
            try {
                body = fetchHttpBody(url, 12000);
            } catch (Throwable t) {
                error = t.getMessage();
            }
            final String bodyFinal = body;
            final String errorFinal = error;
            AndroidUtilities.runOnUIThread(() -> {
                if (bodyFinal == null) {
                    Toast.makeText(getContext(),
                            "Не удалось скачать подписку" + (errorFinal != null ? ": " + errorFinal : ""),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                final int added = PrimeVpnServerStore.addServersFromText(nameHint, bodyFinal);
                afterImport(added, bodyFinal);
            });
        });
    }

    private static String fetchHttpBody(String url, int timeoutMs) throws Exception {
        final HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "PrimeGram");
        conn.setRequestMethod("GET");
        try {
            final int code = conn.getResponseCode();
            final InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) {
                throw new IllegalStateException("HTTP " + code);
            }
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buf = new byte[4096];
            int n;
            int total = 0;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
                if (total > 2 * 1024 * 1024) {
                    break;
                }
            }
            in.close();
            if (code >= 400) {
                throw new IllegalStateException("HTTP " + code);
            }
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }

    private String readClipboard() {
        try {
            final Context context = getParentActivity() != null ? getParentActivity() : getContext();
            if (context == null) {
                return "";
            }
            final ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) {
                return "";
            }
            final ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                return "";
            }
            final CharSequence text = clip.getItemAt(0).coerceToText(context);
            return text != null ? text.toString() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private String readClipboardIfShareOrSub() {
        final String clip = readClipboard();
        if (PrimeVpnServerStore.looksLikeSubscriptionUrl(clip.trim())
                || !PrimeVpnServerStore.extractShareLinks(clip).isEmpty()) {
            return clip;
        }
        return "";
    }

    private void showAddServerDialog(String prefill) {
        if (getParentActivity() == null) {
            return;
        }
        final FrameLayout container = new FrameLayout(getParentActivity());
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(6), AndroidUtilities.dp(24), AndroidUtilities.dp(6));

        final EditTextBoldCursor nameInput = new EditTextBoldCursor(getParentActivity());
        nameInput.setHint("Название (необязательно, иначе из #remark)");
        nameInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameInput.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        nameInput.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        nameInput.setBackground(Theme.createEditTextDrawable(getParentActivity(), true));
        nameInput.setSingleLine(true);
        nameInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        nameInput.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

        final EditTextBoldCursor urlInput = new EditTextBoldCursor(getParentActivity());
        urlInput.setHint("Вставьте ключи или URL подписки");
        urlInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        urlInput.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        urlInput.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        urlInput.setBackground(Theme.createEditTextDrawable(getParentActivity(), true));
        urlInput.setSingleLine(false);
        urlInput.setMaxLines(8);
        urlInput.setMinLines(3);
        urlInput.setGravity(Gravity.TOP | Gravity.LEFT);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        urlInput.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        if (!TextUtils.isEmpty(prefill)) {
            urlInput.setText(prefill);
            urlInput.setSelection(prefill.length());
        }

        final LinearLayout column = new LinearLayout(getParentActivity());
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(nameInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        column.addView(urlInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        container.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        final AlertDialog dialog = new AlertDialog.Builder(getParentActivity())
                .setTitle("Новые ключи")
                .setView(container)
                .setPositiveButton("Добавить", (d, which) -> {
                    final String url = urlInput.getText() != null ? urlInput.getText().toString().trim() : "";
                    if (url.isEmpty()) {
                        return;
                    }
                    final String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
                    if (PrimeVpnServerStore.looksLikeSubscriptionUrl(url)) {
                        importSubscription(url, name);
                        return;
                    }
                    final int added = PrimeVpnServerStore.addServersFromText(name, url);
                    afterImport(added, url);
                })
                .setNeutralButton("Буфер", null)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create();
        dialog.show();
        final View pasteButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (pasteButton != null) {
            pasteButton.setOnClickListener(v -> {
                final String clip = readClipboard();
                if (!TextUtils.isEmpty(clip)) {
                    urlInput.setText(clip);
                    urlInput.setSelection(clip.length());
                } else {
                    Toast.makeText(getContext(), "Буфер пуст", Toast.LENGTH_SHORT).show();
                }
            });
        }
        urlInput.requestFocus();
        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(urlInput), 80);
    }
}
