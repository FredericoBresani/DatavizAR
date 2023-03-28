package com.example.datavizar;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.util.List;
import java.util.Map;

public class CarregarDadosActivity2 extends AppCompatActivity {

    Spinner spnColNomes;
    Spinner spnColValores;
    ArrayAdapter<String> adapterSpnNomes;
    ArrayAdapter<String> adapterSpnValores;
    RecyclerView recyclerTabela;
    DataAdapter adapterRecycler;
    List<DataModel> dataSlice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_carregar_dados2);

        //Variáveis contendo dados para spinners e recyclerView
        String[] cols = DataModel.getNomesColunas();
        List<Map<String, String>> dataSet = DataModel.getDataSet();
        List<Map<String, String>> dataset2 = DataModel.getDataSet();

        //Configurar spinners com lista dos nomes das colunas ("cols")
        spnColNomes = findViewById(R.id.spinColunaNomes);
        spnColValores = findViewById(R.id.spinColunaValores);

          //adapters dos spinners: setados com dados de "cols" e layouts default para "spinner item" e para "dropdown".
        adapterSpnNomes = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,cols);
        adapterSpnValores = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,cols);
        adapterSpnNomes.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        adapterSpnValores.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

          // Aplicar adapter aos spinners e seleciona item selecionado (primeira exibição)
        spnColNomes.setAdapter(adapterSpnNomes);
        spnColValores.setAdapter(adapterSpnValores);
        spnColValores.setSelection(1);

          //configurar eventListeners para os spinners
        AdapterView.OnItemSelectedListener spinnerListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                atualizarDados((String) spnColNomes.getSelectedItem(),
                               (String) spnColValores.getSelectedItem());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // Do nothing
            }
        };
        spnColNomes.setOnItemSelectedListener(spinnerListener);
        spnColValores.setOnItemSelectedListener(spinnerListener);

        recyclerTabela = findViewById(R.id.recycler);
        setRecyclerView();

    }
    private void atualizarDados(String colNomes, String colValores) {
        try {
            dataSlice = DataModel.createDataSlice(colNomes, colValores);
        } catch (Exception e) { //NumberFormat
            dataSlice = null;
            showAlerta("Selecione uma coluna contendo nomes para \"Categoria\" e uma coluna numérica para \"Valores\"!");
        } finally {
            adapterRecycler.setDataSlice(dataSlice);
            adapterRecycler.notifyDataSetChanged();
        }
    }

    private void setRecyclerView() {
        recyclerTabela.setHasFixedSize(true);
        recyclerTabela.setLayoutManager(new LinearLayoutManager(this));
        adapterRecycler = new DataAdapter(this, dataSlice);
        recyclerTabela.setAdapter(adapterRecycler);
    }

    private void showAlerta(String msg) {
        AlertDialog.Builder alerta = new AlertDialog.Builder(this);
        alerta.setMessage(msg);
        alerta.show();
    }

    public void openAR(View btOk) {
        // todo: pegar selected items de dataSlice, passar para próxima Activity (Visualização AR)
        Log.d("Entrooou", toString());
        startActivity(new Intent(CarregarDadosActivity2.this, ARVisualizerActivity.class));
    }


}
