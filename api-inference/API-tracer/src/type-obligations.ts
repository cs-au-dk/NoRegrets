
export type Obligations = Set<Obligation>;

export interface Obligation {}
export class Subtype implements Obligation { }
export class Supertype implements Obligation { }

export class ObligationsOP {
    static equality = new Set([new Subtype(), new Supertype()]);
    static sub = new Set([new Subtype()]);
    static sup = new Set([new Supertype()]);
    static none = new Set([]);

    static withAlso(s: Obligations, o: Obligation): Obligations {
        return ObligationsOP.none;
    }
    static invert(s: Obligations): Obligations {
        return ObligationsOP.none;
    }
}