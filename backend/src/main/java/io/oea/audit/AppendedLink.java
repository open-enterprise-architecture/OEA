package io.oea.audit;

/** Chain position of an appended audit record, as assigned by the database. */
public record AppendedLink(long id, long chainSeq, byte[] recordHash) {

}
