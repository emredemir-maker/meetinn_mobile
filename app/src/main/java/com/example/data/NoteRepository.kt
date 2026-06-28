package com.example.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()

    suspend fun insert(note: Note): Long = noteDao.insertNote(note)

    suspend fun deleteById(id: Int) = noteDao.deleteNoteById(id)
}
