package com.xavierclavel.cooknco.di

import android.content.Context
import com.xavierclavel.cooknco.data.createAuthDataStore

/**
 * Wires the object graph from an Android [Context]. Keeps DataStore off the
 * application module's classpath — it only needs to hand over a context.
 */
fun AppGraph.initFor(context: Context) = init { createAuthDataStore(context) }
