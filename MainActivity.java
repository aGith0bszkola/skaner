package com.example.reader;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {


    private static final String SCRIPT_URL = "https://script.google.com/macros/s/AKfycbwuKvo7uigh2itgimGih2r1yqUXiKwXhKUwdSOColwG7hi5iw973yAdj2-1t7YGpgwD/exec";

    // KODY MASTER
    private static final String KOD_PLANDEKA = "0101010101";
    private static final String KOD_LODOWKA = "0202020202";

    private EditText hiddenInput;
    private TextView textStatus;
    private TextView textLastScan;
    private Button btnReset;
    private Button btnDelete;

    private String aktualneID = "";
    private SharedPreferences prefs;

    // Klient obsługujący przekierowania Google (302)
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicjalizacja widoków
        hiddenInput = findViewById(R.id.hiddenInput);
        textStatus = findViewById(R.id.textStatus);
        textLastScan = findViewById(R.id.textLastScan);
        btnReset = findViewById(R.id.btnReset);
        btnDelete = findViewById(R.id.btnDelete);

        // Pamięć trwała
        prefs = getSharedPreferences("SkanerPrefs", Context.MODE_PRIVATE);
        aktualneID = prefs.getString("wybrany_tryb", "");

        if (!aktualneID.isEmpty()) {
            pokazTrybPracy();
        }

        hiddenInput.requestFocus();

        // OBSŁUGA PRZYCISKU RESET
        btnReset.setOnClickListener(v -> {
            aktualneID = "";
            prefs.edit().putString("wybrany_tryb", "").commit();
            textStatus.setText("ZESKANUJ KOD MASTER\n(Plandeka lub Lodówka)");
            textStatus.setTextColor(Color.parseColor("#333333"));
            textLastScan.setText("Tryb wyboru...");
            btnReset.setVisibility(View.GONE);
            btnDelete.setVisibility(View.GONE);
            hiddenInput.requestFocus();
        });

        // OBSŁUGA PRZYCISKU USUŃ (Podwójne potwierdzenie)
        btnDelete.setOnClickListener(v -> {
            potwierdzUsuniecie(1);
        });

        // OBSŁUGA SKANERA (ENTER)
        hiddenInput.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                // Reagujemy tylko na puszczenie klawisza, ale połykamy oba zdarzenia
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    String code = hiddenInput.getText().toString().trim();
                    hiddenInput.setText("");
                    if (!code.isEmpty()) {
                        handleScannerInput(code);
                    }
                }
                return true; // BLOKADA: ENTER nie "kliknie" w żaden przycisk na ekranie
            }
            return false;
        });
    }

    private void handleScannerInput(String code) {
        // Jeśli tryb jest pusty -> Ustawiamy Mastera
        if (aktualneID.isEmpty()) {
            if (code.equals(KOD_PLANDEKA)) {
                aktualneID = "plandeka";
                zapiszTryb();
            } else if (code.equals(KOD_LODOWKA)) {
                aktualneID = "lodowka";
                zapiszTryb();
            } else {
                Toast.makeText(this, "NAJPIERW ZESKANUJ MASTER KOD!", Toast.LENGTH_SHORT).show();
            }
        }
        // Jeśli tryb wybrany -> Skanujemy towar
        else {
            if (code.equals(KOD_PLANDEKA) || code.equals(KOD_LODOWKA)) {
                Toast.makeText(this, "Tryb " + aktualneID + " już aktywny!", Toast.LENGTH_SHORT).show();
            } else {
                textLastScan.setText("Wysyłanie: " + code);
                textLastScan.setTextColor(Color.BLACK);
                processAndSend(code);
            }
        }
        hiddenInput.requestFocus();
    }

    private void zapiszTryb() {
        prefs.edit().putString("wybrany_tryb", aktualneID).commit();
        pokazTrybPracy();
    }

    private void pokazTrybPracy() {
        textStatus.setText("TRYB: " + aktualneID.toUpperCase());
        int kolor = aktualneID.equals("lodowka") ? Color.BLUE : Color.parseColor("#FF5722");
        textStatus.setTextColor(kolor);
        textLastScan.setText("Gotowy na towary...");
        btnReset.setVisibility(View.VISIBLE);
        hiddenInput.requestFocus();
    }

    private void processAndSend(String czystyKod) {
        // Generowanie kodu binarnego
        byte[] bytes = czystyKod.getBytes();
        StringBuilder binary = new StringBuilder();
        for (byte b : bytes) {
            binary.append(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0')).append(" ");
        }

        HttpUrl url = HttpUrl.parse(SCRIPT_URL).newBuilder()
                .addQueryParameter("kontener", aktualneID)
                .addQueryParameter("kod", binary.toString())
                .addQueryParameter("raw", czystyKod)
                .build();

        Request request = new Request.Builder().url(url).build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    textLastScan.setText("BŁĄD SIECI!");
                    textLastScan.setTextColor(Color.RED);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        textLastScan.setText("ZAPISANO: " + czystyKod);
                        textLastScan.setTextColor(Color.parseColor("#2E7D32"));
                        btnDelete.setVisibility(View.VISIBLE); // Pokazujemy przycisk usuwania po sukcesie
                    });
                }
                response.close();
            }
        });
    }

    private void potwierdzUsuniecie(int krok) {
        String msg = (krok == 1) ? "Czy na pewno usunąć OSTATNI rekord?" : "UWAGA! Rekord zostanie trwale skasowany z bazy i dokumentu!";

        new AlertDialog.Builder(this)
                .setTitle("Potwierdzenie")
                .setMessage(msg)
                .setPositiveButton("TAK", (dialog, which) -> {
                    if (krok == 1) potwierdzUsuniecie(2);
                    else wykonajUsuniecie();
                })
                .setNegativeButton("ANULUJ", null)
                .show();
    }

    private void wykonajUsuniecie() {
        textLastScan.setText("Usuwanie...");

        HttpUrl url = HttpUrl.parse(SCRIPT_URL).newBuilder()
                .addQueryParameter("action", "delete")
                .build();

        Request request = new Request.Builder().url(url).build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> textLastScan.setText("BŁĄD SERWERA!"));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String resText = response.body().string();
                runOnUiThread(() -> {

                    if (resText.contains("<!DOCTYPE")) {
                        textLastScan.setText("Usunieto");
                    } else {
                        textLastScan.setText(resText);
                    }
                    textLastScan.setTextColor(Color.BLUE);
                });
            }
        });
    }
}