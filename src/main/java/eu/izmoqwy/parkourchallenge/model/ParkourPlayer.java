package eu.izmoqwy.parkourchallenge.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class ParkourPlayer {

    private UUID uniqueId;
    @Setter
    private int experience;

}
