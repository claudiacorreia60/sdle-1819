package utils;

import java.io.Serializable;

public class Pair<FST, SND> implements Serializable {

    private FST fst;
    private SND snd;

    public Pair(FST fst, SND snd) {
        this.fst = fst;
        this.snd = snd;
    }

    public FST getFst() {
        return fst;
    }

    public void setFst(FST fst) {
        this.fst = fst;
    }

    public SND getSnd() {
        return snd;
    }

    public void setSnd(SND snd) {
        this.snd = snd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return getFst().equals(pair.getFst()) &&
                getSnd().equals(pair.getSnd());
    }

    @Override
    public String toString() {
        return "Pair{" +
                "fst=" + fst +
                ", snd=" + snd +
                '}';
    }
}
