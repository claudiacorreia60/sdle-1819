package utils;

import java.io.Serializable;

public class Triple<FST, SND, TRD> implements Serializable {
    private FST fst;
    private SND snd;
    private TRD trd;

    public Triple(FST fst, SND snd, TRD trd) {
        this.fst = fst;
        this.snd = snd;
        this.trd = trd;
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

    public TRD getTrd() {
        return trd;
    }

    public void setTrd(TRD trd) {
        this.trd = trd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Triple<?, ?, ?> triple = (Triple<?, ?, ?>) o;
        return getFst().equals(triple.getFst()) &&
                getSnd().equals(triple.getSnd()) &&
                getTrd().equals(triple.getTrd());
    }

    @Override
    public String toString() {
        return "Triple{" +
                "fst=" + fst +
                ", snd=" + snd +
                ", trd=" + trd +
                '}';
    }
}
