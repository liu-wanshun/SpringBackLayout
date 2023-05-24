# Spring Back Layout

[![jitpack](https://jitpack.io/v/liu-wanshun/SpringBackLayout.svg)](https://jitpack.io/#liu-wanshun/SpringBackLayout)
[![license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)

**A wrapper ViewGroup that provides an iOS Look-And-Feel Overscroll Effect**

### Note

This is the layout used in MIUI Setting App.

### Screen shot

<div align="center">
  <table align="center" border="0" >
  <tr>
    <td> <img width="360" src="https://user-images.githubusercontent.com/33343210/82741450-1ca06280-9d7c-11ea-9986-ad2a83673e23.gif"/></td>
  </tr>
</table>
  </div>

### Adding to project

Add it in your root settings.gradle

```groovy
dependencyResolutionManagement {
    repositories {
        //...
        maven { url "https://jitpack.io" }
    }
}
```

Add the dependency in your build.gradle.

```groovy
  implementation "com.github.liu-wanshun:SpringBackLayout:-SNAPSHOT"
```

### Usage

Wrap any scrollable view in the SpringBackLayout, like RecyclerView, ListView or NestedScrollView.

```xml

<com.lws.springback.view.SpringBackLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/root"
    android:layout_width="match_parent" 
    android:layout_height="match_parent"
    app:scrollableView="@id/recycler_view"
    app:scrollOrientation="vertical" >

    <androidx.recyclerview.widget.RecyclerView 
        android:id="@+id/recycler_view"
        android:layout_width="match_parent" 
        android:layout_height="match_parent" />

</com.lws.springback.view.SpringBackLayout>
```