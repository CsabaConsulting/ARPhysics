package dev.csaba.arphysics;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class ChooserActivity extends AppCompatActivity {
    public static final String SIMULATION_SCENARIO = "simulation_scenario";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chooser);

        Button towerChoiceButton = findViewById(R.id.tower_choice);
        towerChoiceButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(SIMULATION_SCENARIO, SimulationScenario.PlankTower);
            startActivity(intent);
        });

        Button collisionBoxChoiceButton = findViewById(R.id.collision_box_choice);
        collisionBoxChoiceButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(SIMULATION_SCENARIO, SimulationScenario.CollisionBox);
            startActivity(intent);
        });
    }
}
