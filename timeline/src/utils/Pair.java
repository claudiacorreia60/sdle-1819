package utils;

import java.util.Objects;

public class Pair<FST, SND> {

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
        if (o instanceof Pair) {
            Pair pair = (Pair) o;
            if (!Objects.equals(fst, pair.fst)) {
                return false;
            }
            if (!Objects.equals(snd, pair.snd)) {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "Pair{" +
                "fst=" + fst +
                ", snd=" + snd +
                '}';
    }
}
